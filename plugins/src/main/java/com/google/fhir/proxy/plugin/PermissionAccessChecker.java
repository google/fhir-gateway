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
package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.proxy.FhirUtil;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.interfaces.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionAccessChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(PermissionAccessChecker.class);
  private final PatientFinder patientFinder;
  private final List<String> userRoles;

  private PermissionAccessChecker(List<String> userRoles, PatientFinder patientFinder) {
    Preconditions.checkNotNull(userRoles);
    Preconditions.checkNotNull(patientFinder);
    this.patientFinder = patientFinder;
    this.userRoles = userRoles;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      //  return processBundle(requestDetails);
    } else {

      // Processing
      boolean userHasRole =
          checkIfRoleExists(getAdminRoleName(requestDetails.getResourceName()), userRoles)
              || checkIfRoleExists(
                  getRelevantRoleName(
                      requestDetails.getResourceName(), requestDetails.getRequestType().name()),
                  userRoles);

      switch (requestDetails.getRequestType()) {
        case GET:
        case DELETE:
          return processGetOrDelete(userHasRole);
        case POST:
          return processPost(requestDetails, userHasRole);
        case PUT:
          return processPut(requestDetails, userHasRole);
        default:
          // TODO handle other cases like PATCH
          return NoOpAccessDecision.accessDenied();
      }
    }

    return NoOpAccessDecision.accessDenied();
  }

  private AccessDecision processGetOrDelete(boolean userHasRole) {
    return userHasRole ? NoOpAccessDecision.accessGranted() : NoOpAccessDecision.accessDenied();
  }

  private AccessDecision processPost(RequestDetailsReader requestDetails, boolean userHasRole) {

    // Run this to checks if FHIR Resource is different from URI endpoint resource type
    patientFinder.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(userHasRole);
  }

  private AccessDecision processPut(RequestDetailsReader requestDetails, boolean userHasRole) {
    if (!userHasRole) {
      logger.error("The current user does not have required Role; denying access!");
      return NoOpAccessDecision.accessDenied();
    }

    String patientId = FhirUtil.getIdOrNull(requestDetails);
    if (patientId == null) {
      // This is an invalid PUT request; note we are not supporting "conditional updates".
      logger.error("The provided Resource has no ID; denying access!");
      return NoOpAccessDecision.accessDenied();
    }

    // We do not allow direct resource PUT, so Patient ID must be returned
    String authorizedPatientId = patientFinder.findPatientFromParams(requestDetails);

    // Retrieve patient ids in resource. Also checks if FHIR Resource is different from URI endpoint
    // resource type
    Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);

    return new NoOpAccessDecision(patientIds.contains(authorizedPatientId));
  }

  private String getRelevantRoleName(String resourceName, String methodType) {
    return methodType + "_" + resourceName.toUpperCase();
  }

  private String getAdminRoleName(String resourceName) {
    return "MANAGE_" + resourceName.toUpperCase();
  }

  private boolean checkIfRoleExists(String roleName, List<String> existingRoles) {
    return existingRoles.contains(roleName);
  }

  @Named(value = "permission")
  static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String REALM_ACCESS_CLAIM = "realm_access";

    private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
      Claim claim = jwt.getClaim(REALM_ACCESS_CLAIM);
      Map<String, Object> roles = claim.asMap();
      List<Object> collection = roles.values().stream().collect(Collectors.toList());
      List<String> rolesList = new ArrayList<>();
      List<Object> collectionPart = (List<Object>) collection.get(0);
      for (Object collect : collectionPart) {
        rolesList.add(collect.toString());
      }
      return rolesList;
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      List<String> userRoles = getUserRolesFromJWT(jwt);
      return new PermissionAccessChecker(userRoles, patientFinder);
    }
  }
}
