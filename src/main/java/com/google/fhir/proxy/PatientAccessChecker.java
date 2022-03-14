/*
 * Copyright 2021-2022 Google LLC
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
package com.google.fhir.proxy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
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
  private final FhirParamUtil fhirParamUtil;

  private PatientAccessChecker(String authorizedPatientId, FhirParamUtil fhirParamUtil) {
    Preconditions.checkNotNull(authorizedPatientId);
    Preconditions.checkNotNull(fhirParamUtil);
    this.authorizedPatientId = authorizedPatientId;
    this.fhirParamUtil = fhirParamUtil;
  }

  @Override
  public AccessDecision checkAccess(RequestDetails requestDetails) {

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
      default:
        // TODO handle other cases like PATCH and DELETE
        return NoOpAccessDecision.accessDenied();
    }
  }

  private AccessDecision processGet(RequestDetails requestDetails) {
    String patientId = fhirParamUtil.findPatientId(requestDetails);
    return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
  }

  private AccessDecision processPost(RequestDetails requestDetails) {
    // This AccessChecker does not accept new patients.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    Set<String> patientIds = fhirParamUtil.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
  }

  private AccessDecision processPut(RequestDetails requestDetails) {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      String patientId = FhirUtil.getIdOrNull(requestDetails);
      if (patientId == null) {
        // This is an invalid PUT request; note we are not supporting "conditional updates".
        logger.error("The provided Patient resource has no ID; denying access!");
        return NoOpAccessDecision.accessDenied();
      }
      return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
    }
    Set<String> patientIds = fhirParamUtil.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
  }

  private AccessDecision processBundle(RequestDetails requestDetails) {
    BundlePatients patientsInBundle = fhirParamUtil.findPatientsInBundle(requestDetails);

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

  static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String PATIENT_CLAIM = "patient_id";

    private final FhirContext fhirContext;

    Factory(RestfulServer server) {
      this.fhirContext = server.getFhirContext();
    }

    private String getPatientId(DecodedJWT jwt) {
      // TODO do some sanity checks on the `patientId` (b/207737513).
      return JwtUtil.getClaimOrDie(jwt, PATIENT_CLAIM);
    }

    public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      FhirParamUtil fhirParamUtil = FhirParamUtil.getInstance(fhirContext);
      return new PatientAccessChecker(getPatientId(jwt), fhirParamUtil);
    }
  }
}
