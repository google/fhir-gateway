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

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.proxy.*;
import com.google.fhir.proxy.interfaces.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

import java.util.*;
import java.util.stream.Collectors;

public class PermissionAccessChecker implements AccessChecker {

    private static final Logger logger = LoggerFactory.getLogger(PatientAccessChecker.class);

    private static final String PATIENT = "Patient";
    private static final String ORGANIZATION = "Organization";
    private static final String COMPOSITION = "Composition";
    private final List<String> userRoles;

    private PermissionAccessChecker(List<String> userRoles) {
        this.userRoles = userRoles;
    }

    @Override
    public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

        switch (requestDetails.getRequestType()) {
        case GET:
            if ((requestDetails.getResourceName()).equals(PATIENT) || (requestDetails.getResourceName()).equals(ORGANIZATION) || (requestDetails.getResourceName()).equals(ORGANIZATION)) {
                boolean result = checkIfRoleExists(getRelevantRoleName(requestDetails.getResourceName(), requestDetails.getRequestType()
                  .name()), userRoles);
                if (result) {
                    logger.info("Access Granted");
                    return NoOpAccessDecision.accessGranted();
                } else {
                    logger.info("Access Denied");
                    return NoOpAccessDecision.accessDenied();
                }
            }
        case POST:

        case PUT:

        case PATCH:

        default:
            // TODO handle other cases like DELETE
            return NoOpAccessDecision.accessDenied();
        }
    }

    private String getRelevantRoleName(String resourceName, String methodType) {
        return methodType + "_" + resourceName.toUpperCase();
    }

    private boolean checkIfRoleExists(String roleName, List<String> existingRoles) {
        if (existingRoles.contains(roleName)) {
            return true;
        } else {
            return false;
        }
    }

    @Named(value = "permission")
    static class Factory implements AccessCheckerFactory {

        private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
            Claim claim = jwt.getClaim("realm_access");
            Map<String, Object> roles = claim.asMap();
            List<Object> collection = roles.values()
              .stream()
              .collect(Collectors.toList());
            List<String> rolesList = new ArrayList<>();
            List<Object> collectionPart = (List<Object>) collection.get(0);
            for (Object collect : collectionPart) {
                rolesList.add(collect.toString());
            }
            return rolesList;
        }

        @Override
        public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient, FhirContext fhirContext, PatientFinder patientFinder) {
            List<String> userRoles = getUserRolesFromJWT(jwt);
            return new PermissionAccessChecker(userRoles);
        }
    }
}
