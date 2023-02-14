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
import lombok.Getter;

/**
 * This class models the SMART-on-FHIR permission scopes that are meant ot be used for accessing
 * clinical data. The constraints in this class are according to the official guidelines here:
 * https://build.fhir.org/ig/HL7/smart-app-launch/scopes-and-launch-context.html#scopes-for-requesting-clinical-data
 */
public class SmartFhirScope {
  private static final Pattern VALID_SCOPE_PATTERN =
      Pattern.compile(
          "(\\buser|patient|system\\b)(\\/((\\*)|([a-zA-Z]+)))(\\.((\\*)|([cruds]+)|(\\bread|write\\b)))");
  static final String ALL_RESOURCE_TYPES_WILDCARD = "*";
  private static final String ALL_RESOURCE_PERMISSIONS_WILDCARD = "*";
  private static final String SMART_V1_READ_RESOURCE_PERMISSIONS = "read";
  private static final String SMART_V1_WRITE_RESOURCE_PERMISSIONS = "write";

  @Getter private final PrincipalContext principalContext;
  @Getter private final String resourceType;
  @Getter private final Set<Permission> permissions;

  private SmartFhirScope(
      PrincipalContext principalContext, String resourceType, Set<Permission> resourcePermissions) {
    this.principalContext = principalContext;
    this.permissions = resourcePermissions;
    this.resourceType = resourceType;
  }

  static List<SmartFhirScope> extractSmartFhirScopesFromTokens(List<String> tokens) {
    List<SmartFhirScope> scopes = new ArrayList<>();
    for (String scope : tokens) {
      if (VALID_SCOPE_PATTERN.matcher(scope).matches()) {
        scopes.add(createSmartScope(scope));
      }
    }
    return scopes;
  }

  private static SmartFhirScope createSmartScope(String scope) {
    String[] split = scope.split("/");
    PrincipalContext principalContext = PrincipalContext.getPrincipalContext(split[0]);
    String[] permissionSplit = split[1].split("\\.");
    if (!isValidResourceType(permissionSplit[0])) {
      throw new IllegalArgumentException(
          String.format("Invalid resource type %s", permissionSplit[0]));
    }
    String resourceType = permissionSplit[0];
    Set<Permission> permissions = extractPermissions(permissionSplit[1]);
    return new SmartFhirScope(principalContext, resourceType, permissions);
  }

  private static boolean isValidResourceType(String resourceType) {
    return ALL_RESOURCE_TYPES_WILDCARD.equals(resourceType)
        || FhirUtil.isValidFhirResourceType(resourceType);
  }

  private static Set<Permission> extractPermissions(String permissionString) {
    Set<Permission> permissions = new HashSet<>();
    if (ALL_RESOURCE_PERMISSIONS_WILDCARD.equals(permissionString)) {
      permissions.addAll(List.of(Permission.values()));
      return permissions;
    }
    // We will support both v1 and v2 versions of the permissions:
    // https://build.fhir.org/ig/HL7/smart-app-launch/scopes-and-launch-context.html#scopes-for-requesting-clinical-data
    if (SMART_V1_READ_RESOURCE_PERMISSIONS.equals(permissionString)) {
      permissions.add(Permission.READ);
      permissions.add(Permission.SEARCH);
      return permissions;
    }
    if (SMART_V1_WRITE_RESOURCE_PERMISSIONS.equals(permissionString)) {
      permissions.add(Permission.CREATE);
      permissions.add(Permission.UPDATE);
      permissions.add(Permission.DELETE);
      return permissions;
    }
    char[] permissionTokens = permissionString.toCharArray();
    int permissionTokensCounter = 0;
    // SMART guidelines recommend enforcing order in the permissions string:
    // https://build.fhir.org/ig/HL7/smart-app-launch/scopes-and-launch-context.html#scopes-for-requesting-clinical-data
    for (Permission permission : Permission.values()) {
      if (Permission.getPermission(permissionTokens[permissionTokensCounter]) == permission) {
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

  enum PrincipalContext {
    USER,
    PATIENT,
    SYSTEM;

    static PrincipalContext getPrincipalContext(String principal) {
      return PrincipalContext.valueOf(principal.toUpperCase());
    }
  }

  enum Permission {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    SEARCH;

    static Permission getPermission(char permissionCode) {
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
}
