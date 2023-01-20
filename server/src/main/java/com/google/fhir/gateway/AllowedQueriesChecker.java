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

import com.google.common.collect.Sets;
import com.google.fhir.gateway.AllowedQueriesConfig.AllowedQueryEntry;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
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
    if (!entry.getPath().equals(requestDetails.getRequestPath())) {
      return false;
    }
    Set<String> matchedQueryParams = Sets.newHashSet();
    for (Entry<String, String> expectedParam : entry.getQueryParams().entrySet()) {
      String[] actualQueryValue = requestDetails.getParameters().get(expectedParam.getKey());
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
    if (!entry.isAllowExtraParams()
        && matchedQueryParams.size() != requestDetails.getParameters().size()) {
      return false;
    }
    logger.info(
        "Allowed-queries entry {} matched query {}", entry, requestDetails.getCompleteUrl());
    return true;
  }
}
