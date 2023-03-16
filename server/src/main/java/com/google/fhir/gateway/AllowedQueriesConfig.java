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
package com.google.fhir.gateway;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * This is a minimal implementation of a configuration for a query allow-list. Each allowed query is
 * defined by its expected path and set of expected query parameters.
 */
class AllowedQueriesConfig {
  public static final String MATCHES_ANY_VALUE = "ANY_VALUE";

  public static Pattern MATCHES_PATH_ANY_VALUE_PATTERN = Pattern.compile("^[a-zA-Z]*(/ANY_VALUE)$");

  // Note this is a very simplistic config for an allow-listed query template; we should expand this
  // with information from the access token once needed.
  @Getter
  public static class AllowedQueryEntry {
    // Supports exact path match and special path matching @MATCHES_PATH_ANY_VALUE_PATTERN
    // @MATCHES_PATH_ANY_VALUE_PATTERN allows any value of the path variable for a basePath
    private String path;

    // Http request type allowed by the config.
    private String requestType;
    private Map<String, String> queryParams;
    // If true, this means other parameters not listed in `queryParams` are allowed too.
    private boolean allowExtraParams;
    // If true, this means all parameters in `queryParams` are required,  i.e., none are optional.
    private boolean allParamsRequired;

    // If true, this means we allow unrestricted access to the allowed queries.
    private boolean allowUnAuthenticatedRequests;

    @Override
    public String toString() {
      String builder =
          "path="
              + path
              + " queryParams="
              + Arrays.toString(queryParams.entrySet().toArray())
              + " allowExtraParams="
              + allowExtraParams
              + " allParamsRequired="
              + allParamsRequired
              + " allowUnAuthenticatedRequests="
              + allowUnAuthenticatedRequests
              + " requestType="
              + requestType;
      return builder;
    }
  }

  @Getter List<AllowedQueryEntry> entries;
}
