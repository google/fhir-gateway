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
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.proxy.BundleResources;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.ResourceFinderImp;
import com.google.fhir.proxy.interfaces.AccessChecker;
import com.google.fhir.proxy.interfaces.AccessCheckerFactory;
import com.google.fhir.proxy.interfaces.AccessDecision;
import com.google.fhir.proxy.interfaces.NoOpAccessDecision;
import com.google.fhir.proxy.interfaces.PatientFinder;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import com.google.fhir.proxy.interfaces.ResourceFinder;
import java.util.List;
import java.util.Map;
import javax.inject.Named;

public class PermissionAccessChecker implements AccessChecker {
  private final ResourceFinder resourceFinder;
  private final List<String> userRoles;

  private PermissionAccessChecker(List<String> userRoles, ResourceFinder resourceFinder) {
    Preconditions.checkNotNull(userRoles);
    Preconditions.checkNotNull(resourceFinder);
    this.resourceFinder = resourceFinder;
    this.userRoles = userRoles;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      return processBundle(requestDetails);

    } else {

      boolean userHasRole =
          checkUserHasRole(
              requestDetails.getResourceName(), requestDetails.getRequestType().name());

      RequestTypeEnum requestType = requestDetails.getRequestType();

      switch (requestType) {
        case GET:
          return processGet(userHasRole);
        case DELETE:
          return processDelete(userHasRole);
        case POST:
          return processPost(userHasRole);
        case PUT:
          return processPut(userHasRole);
        default:
          // TODO handle other cases like PATCH
          return NoOpAccessDecision.accessDenied();
      }
    }
  }

  private boolean checkUserHasRole(String resourceName, String requestType) {
    return checkIfRoleExists(getAdminRoleName(resourceName), this.userRoles)
        || checkIfRoleExists(getRelevantRoleName(resourceName, requestType), this.userRoles);
  }

  private AccessDecision processGet(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processDelete(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision getAccessDecision(boolean userHasRole) {
    return userHasRole ? NoOpAccessDecision.accessGranted() : NoOpAccessDecision.accessDenied();
  }

  private AccessDecision processPost(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processPut(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) {

    List<BundleResources> resourcesInBundle = resourceFinder.findResourcesInBundle(requestDetails);

    // Verify Authorization for individual requests in Bundle
    for (BundleResources bundleResources : resourcesInBundle) {
      if (!checkUserHasRole(
          bundleResources.getResource().fhirType(), bundleResources.getRequestType().name())) {
        return NoOpAccessDecision.accessDenied();
      }
    }

    return NoOpAccessDecision.accessGranted();
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
    @VisibleForTesting static final String ROLES = "roles";

    private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
      Claim claim = jwt.getClaim(REALM_ACCESS_CLAIM);
      Map<String, Object> roles = claim.asMap();
      List<String> rolesList = (List) roles.get(ROLES);
      return rolesList;
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder)
        throws AuthenticationException {
      List<String> userRoles = getUserRolesFromJWT(jwt);
      return new PermissionAccessChecker(userRoles, ResourceFinderImp.getInstance(fhirContext));
    }
  }
}
