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
package com.google.fhir.gateway.plugin;

import com.google.fhir.gateway.FhirUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class SmartFhirScope {
  SmartFhirScopeResourcePrincipalContext principalContext;
  String resourceType;

  public SmartFhirScopeResourcePrincipalContext getPrincipalContext() {
    return principalContext;
  }

  public String getResourceType() {
    return resourceType;
  }

  public Set<SmartFhirScopeResourcePermission> getResourcePermissions() {
    return resourcePermissions;
  }

  Set<SmartFhirScopeResourcePermission> resourcePermissions;

  private SmartFhirScope(
      SmartFhirScopeResourcePrincipalContext principalContext,
      String resourceType,
      Set<SmartFhirScopeResourcePermission> resourcePermissions) {
    this.principalContext = principalContext;
    this.resourcePermissions = resourcePermissions;
    this.resourceType = resourceType;
  }

  private static final Pattern PATIENT_SCOPE_PATTERN =
      Pattern.compile(
          "(\\buser|patient|system\\b)(\\/((\\*)|([a-zA-Z]+)))(\\.((\\*)|([cruds]+)|(\\bread|write\\b)))");
  static final String ALL_RESOURCE_TYPES_WILDCARD = "*";
  static final String ALL_RESOURCE_PERMISSIONS_WILDCARD = "*";
  static final String SMART_V1_READ_RESOURCE_PERMISSIONS = "read";
  static final String SMART_V1_WRITE_RESOURCE_PERMISSIONS = "write";

  private static final List<SmartFhirScopeResourcePermission> resourcePermissionsSequence =
      List.of(
          SmartFhirScopeResourcePermission.CREATE,
          SmartFhirScopeResourcePermission.READ,
          SmartFhirScopeResourcePermission.UPDATE,
          SmartFhirScopeResourcePermission.DELETE,
          SmartFhirScopeResourcePermission.SEARCH);

  static List<SmartFhirScope> extractSmartFhirScopesFromTokens(List<String> tokens) {
    List<SmartFhirScope> scopes = new ArrayList<>();
    for (String scope : tokens) {
      if (PATIENT_SCOPE_PATTERN.matcher(scope).matches()) {
        scopes.add(createSmartScope(scope));
      }
    }
    return scopes;
  }

  private static SmartFhirScope createSmartScope(String scope) {
    String[] principalTokenSplit = scope.split("/");
    SmartFhirScopeResourcePrincipalContext principalContext =
        SmartFhirScopeResourcePrincipalContext.getPrincipalContext(principalTokenSplit[0]);
    String[] resourceTypeAndPermissionSplit = principalTokenSplit[1].split("\\.");
    if (!isValidResourceType(resourceTypeAndPermissionSplit[0])) {
      throw new IllegalArgumentException(
          String.format("Invalid resource type %s", resourceTypeAndPermissionSplit[0]));
    }
    String resourceType = resourceTypeAndPermissionSplit[0];
    Set<SmartFhirScopeResourcePermission> permissions =
        extractPermissions(resourceTypeAndPermissionSplit[1]);
    return new SmartFhirScope(principalContext, resourceType, permissions);
  }

  private static boolean isValidResourceType(String resourceType) {
    return ALL_RESOURCE_TYPES_WILDCARD.equals(resourceType)
        || FhirUtil.isValidFhirResourceType(resourceType);
  }

  private static Set<SmartFhirScopeResourcePermission> extractPermissions(String permissionString) {
    // TODO(anchitag): Assumes that the permissions are in v2 format only
    Set<SmartFhirScopeResourcePermission> permissions = new HashSet<>();
    if (ALL_RESOURCE_PERMISSIONS_WILDCARD.equals(permissionString)) {
      permissions.addAll(resourcePermissionsSequence);
      return permissions;
    }
    // https://build.fhir.org/ig/HL7/smart-app-launch/scopes-and-launch-context.html#scopes-for-requesting-clinical-data
    if (SMART_V1_READ_RESOURCE_PERMISSIONS.equals(permissionString)) {
      permissions.add(SmartFhirScopeResourcePermission.READ);
      permissions.add(SmartFhirScopeResourcePermission.SEARCH);
      return permissions;
    }
    if (SMART_V1_WRITE_RESOURCE_PERMISSIONS.equals(permissionString)) {
      permissions.add(SmartFhirScopeResourcePermission.CREATE);
      permissions.add(SmartFhirScopeResourcePermission.UPDATE);
      permissions.add(SmartFhirScopeResourcePermission.DELETE);
      return permissions;
    }
    char[] permissionTokens = permissionString.toCharArray();
    int permissionTokensCounter = 0;
    for (SmartFhirScopeResourcePermission permission : resourcePermissionsSequence) {
      if (SmartFhirScopeResourcePermission.getPermission(permissionTokens[permissionTokensCounter])
          == permission) {
        permissionTokensCounter++;
        permissions.add(permission);
      }
      if (permissionTokensCounter == permissionTokens.length) {
        break;
      }
    }
    if (permissionTokensCounter != permissionTokens.length) {
      throw new IllegalArgumentException(
          String.format("Invalid permission string %s", permissionString));
    }
    return permissions;
  }
}

enum SmartFhirScopeResourcePrincipalContext {
  USER,
  PATIENT,
  SYSTEM;

  static SmartFhirScopeResourcePrincipalContext getPrincipalContext(String principal) {
    switch (principal.toLowerCase()) {
      case "user":
        return USER;
      case "patient":
        return PATIENT;
      case "system":
        return SYSTEM;
      default:
        throw new IllegalArgumentException(String.format("Invalid principal %s", principal));
    }
  }
}

enum SmartFhirScopeResourcePermission {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  SEARCH;

  static SmartFhirScopeResourcePermission getPermission(char permissionCode) {
    switch (permissionCode) {
      case 'c':
        return CREATE;
      case 'r':
        return READ;
      case 'u':
        return UPDATE;
      case 'd':
        return DELETE;
      case 's':
        return SEARCH;
      default:
        throw new IllegalArgumentException(
            String.format("Invalid permission code. %s", permissionCode));
    }
  }
}
