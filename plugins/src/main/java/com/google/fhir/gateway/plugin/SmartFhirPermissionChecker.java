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

import com.google.fhir.gateway.plugin.SmartFhirScope.Permission;
import com.google.fhir.gateway.plugin.SmartFhirScope.PrincipalContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SmartFhirPermissionChecker {

  private final Map<String, Set<SmartFhirScope.Permission>> permissionsByResourceType;

  SmartFhirPermissionChecker(List<SmartFhirScope> scopes, PrincipalContext permissionContext) {
    this.permissionsByResourceType =
        scopes.stream()
            .filter(smartFhirScope -> smartFhirScope.getPrincipalContext() == permissionContext)
            .collect(
                Collectors.groupingBy(
                    SmartFhirScope::getResourceType,
                    Collectors.flatMapping(
                        resourceScopes -> resourceScopes.getPermissions().stream(),
                        Collectors.toSet())));
  }

  boolean hasPermission(String resourceType, Permission permission) {
    return this.permissionsByResourceType
            .getOrDefault(resourceType, Collections.emptySet())
            .contains(permission)
        || this.permissionsByResourceType
            .getOrDefault(SmartFhirScope.ALL_RESOURCE_TYPES_WILDCARD, Collections.emptySet())
            .contains(permission);
  }
}
