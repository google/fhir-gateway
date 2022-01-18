/*
 * Copyright 2021 Google LLC
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
  private final PatientFinder patientFinder;

  private PatientAccessChecker(String authorizedPatientId, PatientFinder patientFinder) {
    Preconditions.checkNotNull(authorizedPatientId);
    Preconditions.checkNotNull(patientFinder);
    this.authorizedPatientId = authorizedPatientId;
    this.patientFinder = patientFinder;
  }

  public AccessDecision checkAccess(RequestDetails requestDetails) {
    if (requestDetails.getRequestType() == RequestTypeEnum.GET) {
      String patientId = patientFinder.findPatientId(requestDetails);
      return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
    }
    // This AccessChecker does not accept new patients.
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    // Updating Patient resource
    if (requestDetails.getRequestType() == RequestTypeEnum.PUT
        && FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      String patientId = FhirUtil.getIdOrNull(requestDetails);
      if (patientId == null) {
        // This is an invalid PUT request; note we are not supporting "conditional updates".
        logger.error("The provided Patient resource has no ID; denying access!");
        return NoOpAccessDecision.accessDenied();
      }
      return new NoOpAccessDecision(authorizedPatientId.equals(patientId));
    }
    // Creating/updating a non-Patient resource
    if (requestDetails.getRequestType() == RequestTypeEnum.PUT
        || requestDetails.getRequestType() == RequestTypeEnum.POST) {
      Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);
      return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
    }
    // TODO handle other cases like PATCH and DELETE
    return NoOpAccessDecision.accessDenied();
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
      PatientFinder patientFinder = PatientFinder.getInstance(fhirContext);
      return new PatientAccessChecker(getPatientId(jwt), patientFinder);
    }
  }
}
