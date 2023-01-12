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

import com.google.common.collect.Sets;
import com.google.fhir.proxy.AllowedQueriesConfig.AllowedQueryEntry;
import com.google.fhir.proxy.interfaces.AccessChecker;
import com.google.fhir.proxy.interfaces.AccessDecision;
import com.google.fhir.proxy.interfaces.NoOpAccessDecision;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An access-checker that compares the incoming request with a configured set of allowed-queries.
 * The intended use of this checker is to override all other access-checkers, i.e., if the query
 * matches an entry in the config file, access is granted; if it does not or the config file is not
 * provided, other access-checkers should be called. TODO: Add a new access state beside "granted"
 * and "denied", e.g, "deferred".
 */
class AllowedQueriesChecker implements AccessChecker {
  private static final Logger logger = LoggerFactory.getLogger(AllowedQueriesChecker.class);

  private AllowedQueriesConfig config = null;

  AllowedQueriesChecker(String configFile) throws IOException {
    if (configFile != null && !configFile.isEmpty()) {
      try {
        Gson gson = new Gson();
        config = gson.fromJson(new FileReader(configFile), AllowedQueriesConfig.class);
        if (config == null || config.entries == null) {
          throw new IllegalArgumentException("A map with a single `entries` array expected!");
        }
        for (AllowedQueryEntry entry : config.entries) {
          if (entry.getPath() == null) {
            throw new IllegalArgumentException("Allow-list entries should have a path.");
          }
        }
      } catch (IOException e) {
        logger.error("IO error while reading allow-list config file {}", configFile);
        throw e;
      }
    }
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    if (config == null) {
      return NoOpAccessDecision.accessDenied();
    }
    for (AllowedQueryEntry entry : config.entries) {
      if (requestMatches(requestDetails, entry)) {
        return NoOpAccessDecision.accessGranted();
      }
    }
    return NoOpAccessDecision.accessDenied();
  }

  private boolean requestMatches(RequestDetailsReader requestDetails, AllowedQueryEntry entry) {
    if (entry.getMethodType() != null && entry.getMethodType().equals(requestDetails.getRequestType().name()) && requestContainsPathVariables(requestDetails.getRequestPath())) {
      String requestPath = getResourceFromCompleteRequestPath(requestDetails.getRequestPath());
      if (!entry.getPath().equals(requestPath)) {
        return false;
      }
    } else if (!entry.getPath().equals(requestDetails.getRequestPath())) {
      return false;
    } else if (entry.getMethodType() != null && entry.getMethodType().equals(requestDetails.getRequestType().name())) {
      Set<String> matchedQueryParams = Sets.newHashSet();
      for (Entry<String, String> expectedParam : entry.getQueryParams()
              .entrySet()) {
        String[] actualQueryValue = requestDetails.getParameters()
                .get(expectedParam.getKey());
        if (actualQueryValue == null && entry.isAllParamsRequired()) {
          // This allow-list entry does not match the query.
          return false;
        }
        if (actualQueryValue == null) {
          // Nothing else to do for this configured param as it is not present in the query.
          continue;
        }
        if (!AllowedQueriesConfig.MATCHES_ANY_VALUE.equals(expectedParam.getValue())) {
          if (actualQueryValue.length != 1) {
            // We currently do not support multivalued query params in allow-lists.
            return false;
          }
          if (!actualQueryValue[0].equals(expectedParam.getValue())) {
            return false;
          }
        }
        matchedQueryParams.add(expectedParam.getKey());
      }
      if (!entry.isAllowExtraParams() && matchedQueryParams.size() != requestDetails.getParameters()
              .size()) {
        return false;
      }
    } else {
      logger.info("Allowed-queries entry {} matched query {}", entry, requestDetails.getCompleteUrl());
      return true;
    }
    return true;
  }

  private boolean requestContainsPathVariables(String completeRequestPath) {
    String requestResourcePath = trimForwardSlashFromRequestPath(completeRequestPath);
    if(requestResourcePath != null && requestResourcePath.startsWith("/")) {
      requestResourcePath = requestResourcePath.substring(1);
    }
    if(requestResourcePath.contains("/")) {
      return true;
    }
    return false;
  }

  private String getResourceFromCompleteRequestPath(String completeRequestPath) {
   String requestResourcePath = trimForwardSlashFromRequestPath(completeRequestPath);
    if(requestResourcePath.contains("/")) {

      int pathVarIndex = requestResourcePath.indexOf("/");
      String pathVar = requestResourcePath.substring(pathVarIndex+1);
      String requestPath = requestResourcePath.substring(0,pathVarIndex+1);
      requestPath = "/" + requestPath;
      return requestPath;
    }
    return requestResourcePath;
  }

  private String trimForwardSlashFromRequestPath(String completeRequestPath) {
    String requestResourcePath = completeRequestPath;
    if(completeRequestPath.startsWith("/")) {
      requestResourcePath = completeRequestPath.substring(1);
    }
    return requestResourcePath;
  }
}
