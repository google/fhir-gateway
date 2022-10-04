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
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.interfaces.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionAccessChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(PermissionAccessChecker.class);

  private static final String PATIENT = "Patient";
  private static final String ORGANIZATION = "Organization";
  private static final String COMPOSITION = "Composition";
  private final List<String> userRoles;

  private PermissionAccessChecker(List<String> userRoles) {
    this.userRoles = userRoles;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      //  return processBundle(requestDetails);
    } else {

      boolean userHasRole =
          checkIfRoleExists(getAdminRoleName(requestDetails.getResourceName()), userRoles)
              || checkIfRoleExists(
                  getRelevantRoleName(
                      requestDetails.getResourceName(), requestDetails.getRequestType().name()),
                  userRoles);

      switch (requestDetails.getRequestType()) {
        case GET:
          return processGet(userHasRole);
        case POST:
          // return processPost(requestDetails);
        case PUT:
          // return processPut(requestDetails);
        case DELETE:
          // return processDelete(requestDetails);
        default:
          // TODO handle other cases like PATCH
          return NoOpAccessDecision.accessDenied();
      }
    }

    return NoOpAccessDecision.accessDenied();
  }

  private AccessDecision processGet(boolean userHasRole) {
    return userHasRole ? NoOpAccessDecision.accessGranted() : NoOpAccessDecision.accessDenied();
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

    private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
      Claim claim = jwt.getClaim("realm_access");
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
      return new PermissionAccessChecker(userRoles);
    }
  }
}
