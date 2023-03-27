/*
 * Copyright 2021-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import javax.inject.Named;

import com.google.fhir.gateway.BundlePatients;
import com.google.fhir.gateway.FhirUtil;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.*;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_id` claim in the access token to decide whether access to a
 * request should be granted or not.
 */
public class PatientAccessChecker implements AccessChecker {
  private static final Logger logger = LoggerFactory.getLogger(PatientAccessChecker.class);

  private final String authorizedPatientId;
  private final PatientFinder patientFinder;

  protected PatientAccessChecker(String authorizedPatientId, PatientFinder patientFinder) {
    Preconditions.checkNotNull(authorizedPatientId);
    Preconditions.checkNotNull(patientFinder);
    this.authorizedPatientId = authorizedPatientId;
    this.patientFinder = patientFinder;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      return processBundle(requestDetails);
    }

    switch (requestDetails.getRequestType()) {
      case GET:
        return processGet(requestDetails);
      case POST:
        return processPost(requestDetails);
      case PUT:
        return processPut(requestDetails);
      case PATCH:
        return processPatch(requestDetails);
      default:
        // TODO handle other cases like DELETE
        return NoOpAccessDecision.accessDenied();
    }
  }

  private AccessDecision processGet(RequestDetailsReader requestDetails) {
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
  }

  private AccessDecision processPost(RequestDetailsReader requestDetails) {
    // This AccessChecker does not accept new patients.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
  }

  private AccessDecision processPut(RequestDetailsReader requestDetails) {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return checkPatientAccessInUpdate(requestDetails);
    }
    return checkNonPatientAccessInUpdate(requestDetails, HTTPVerb.PUT);
  }

  private AccessDecision processPatch(RequestDetailsReader requestDetails) {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return checkPatientAccessInUpdate(requestDetails);
    }
    return checkNonPatientAccessInUpdate(requestDetails, HTTPVerb.PATCH);
  }

  private AccessDecision checkNonPatientAccessInUpdate(
      RequestDetailsReader requestDetails, HTTPVerb updateMethod) {
    // We do not allow direct resource PUT/PATCH, so Patient ID must be returned
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    if (!patientId.equals(authorizedPatientId)) {
      return NoOpAccessDecision.accessDenied();
    }

    Set<String> patientIds = Sets.newHashSet();
    if (updateMethod == HTTPVerb.PATCH) {
      patientIds =
          patientFinder.findPatientsInPatch(requestDetails, requestDetails.getResourceName());
      if (patientIds.isEmpty()) {
        return NoOpAccessDecision.accessGranted();
      }
    }
    if (updateMethod == HTTPVerb.PUT) {
      patientIds = patientFinder.findPatientsInResource(requestDetails);
    }
    return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
  }

  private AccessDecision checkPatientAccessInUpdate(RequestDetailsReader requestDetails) {
    String patientId = FhirUtil.getIdOrNull(requestDetails);
    if (patientId == null) {
      // This is an invalid PUT request; note we are not supporting "conditional updates".
      logger.error("The provided Patient resource has no ID; denying access!");
      return NoOpAccessDecision.accessDenied();
    }
    return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) {
    BundlePatients patientsInBundle = patientFinder.findPatientsInBundle(requestDetails);

    if (patientsInBundle == null || patientsInBundle.areTherePatientToCreate()) {
      return NoOpAccessDecision.accessDenied();
    }

    if (!patientsInBundle.getUpdatedPatients().isEmpty()
        && !patientsInBundle.getUpdatedPatients().equals(ImmutableSet.of(authorizedPatientId))) {
      return NoOpAccessDecision.accessDenied();
    }

    for (Set<String> refSet : patientsInBundle.getReferencedPatients()) {
      if (!refSet.contains(authorizedPatientId)) {
        return NoOpAccessDecision.accessDenied();
      }
    }
    return NoOpAccessDecision.accessGranted();
  }

  @Named(value = "patient")
  static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String PATIENT_CLAIM = "patient_id";

    private String getPatientId(DecodedJWT jwt) {
      return FhirUtil.checkIdOrFail(JwtUtil.getClaimOrDie(jwt, PATIENT_CLAIM));
    }

    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      return new PatientAccessChecker(getPatientId(jwt), patientFinder);
    }
  }
}
