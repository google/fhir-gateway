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

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.fhir.gateway.ProxyConstants;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSRPSyncAccessDecision implements AccessDecision {
  public static final String SYNC_FILTER_IGNORE_RESOURCES_FILE_ENV =
      "SYNC_FILTER_IGNORE_RESOURCES_FILE";
  public static final String MATCHES_ANY_VALUE = "ANY_VALUE";
  private static final Logger logger = LoggerFactory.getLogger(OpenSRPSyncAccessDecision.class);
  private static final int LENGTH_OF_SEARCH_PARAM_AND_EQUALS = 5;
  private final List<String> syncStrategy;
  private final String applicationId;
  private final boolean accessGranted;

  private final List<String> careTeamIds;

  private final List<String> locationIds;

  private final List<String> organizationIds;
  private IgnoredResourcesConfig config;

  public OpenSRPSyncAccessDecision(
      String applicationId,
      boolean accessGranted,
      List<String> locationIds,
      List<String> careTeamIds,
      List<String> organizationIds,
      List<String> syncStrategy) {
    this.applicationId = applicationId;
    this.accessGranted = accessGranted;
    this.careTeamIds = careTeamIds;
    this.locationIds = locationIds;
    this.organizationIds = organizationIds;
    this.syncStrategy = syncStrategy;
    config = getSkippedResourcesConfigs();
  }

  @Override
  public boolean canAccess() {
    return accessGranted;
  }

  @Override
  public void preProcess(ServletRequestDetails servletRequestDetails) {
    // TODO: Disable access for a user who adds tags to organisations, locations or care teams that
    // they do not have access to
    //  This does not bar access to anyone who uses their own sync tags to circumvent
    //  the filter. The aim of this feature based on scoping was to pre-filter the data for the user
    if (isSyncUrl(servletRequestDetails)) {
      // This prevents access to a user who has no location/organisation/team assigned to them
      if (locationIds.size() == 0 && careTeamIds.size() == 0 && organizationIds.size() == 0) {
        locationIds.add(
            "CR1bAeGgaYqIpsNkG0iidfE5WVb5BJV1yltmL4YFp3o6mxj3iJPhKh4k9ROhlyZveFC8298lYzft8SIy8yMNLl5GVWQXNRr1sSeBkP2McfFZjbMYyrxlNFOJgqvtccDKKYSwBiLHq2By5tRupHcmpIIghV7Hp39KgF4iBDNqIGMKhgOIieQwt5BRih5FgnwdHrdlK9ix");
      }

      // Skip app-wide global resource requests
      if (!shouldSkipDataFiltering(servletRequestDetails)) {

        addSyncFilters(
            servletRequestDetails, getSyncTags(locationIds, careTeamIds, organizationIds));
      }
    }
  }

  /**
   * Adds filters to the {@link ServletRequestDetails} for the _tag property to allow filtering by
   * specific code-url-values that match specific locations, teams or organisations
   *
   * @param servletRequestDetails
   * @param syncTags
   */
  private void addSyncFilters(
      ServletRequestDetails servletRequestDetails, Pair<String, Map<String, String[]>> syncTags) {
    List<String> paramValues = new ArrayList<>();
    Collections.addAll(
        paramValues,
        syncTags
            .getKey()
            .substring(LENGTH_OF_SEARCH_PARAM_AND_EQUALS)
            .split(ProxyConstants.PARAM_VALUES_SEPARATOR));

    String[] prevTagFilters =
        servletRequestDetails.getParameters().get(ProxyConstants.TAG_SEARCH_PARAM);
    if (prevTagFilters != null && prevTagFilters.length > 0) {
      Collections.addAll(paramValues, prevTagFilters);
    }

    servletRequestDetails.addParameter(
        ProxyConstants.TAG_SEARCH_PARAM, paramValues.toArray(new String[0]));
  }

  @Override
  public String postProcess(HttpResponse response) throws IOException {
    return null;
  }

  /**
   * Generates a map of Code.url to multiple Code.Value which contains all the possible filters that
   * will be used in syncing
   *
   * @param locationIds
   * @param careTeamIds
   * @param organizationIds
   * @return Pair of URL to [Code.url, [Code.Value]] map. The URL is complete url
   */
  private Pair<String, Map<String, String[]>> getSyncTags(
      List<String> locationIds, List<String> careTeamIds, List<String> organizationIds) {
    StringBuilder sb = new StringBuilder();
    Map<String, String[]> map = new HashMap<>();

    sb.append(ProxyConstants.TAG_SEARCH_PARAM);
    sb.append(ProxyConstants.Literals.EQUALS);

    addTags(ProxyConstants.LOCATION_TAG_URL, locationIds, map, sb);
    addTags(ProxyConstants.ORGANISATION_TAG_URL, organizationIds, map, sb);
    addTags(ProxyConstants.CARE_TEAM_TAG_URL, careTeamIds, map, sb);

    return new ImmutablePair<>(sb.toString(), map);
  }

  private void addTags(
      String tagUrl,
      List<String> values,
      Map<String, String[]> map,
      StringBuilder urlStringBuilder) {
    int len = values.size();
    if (len > 0) {
      if (urlStringBuilder.length()
          != (ProxyConstants.TAG_SEARCH_PARAM + ProxyConstants.Literals.EQUALS).length()) {
        urlStringBuilder.append(ProxyConstants.PARAM_VALUES_SEPARATOR);
      }

      map.put(tagUrl, values.toArray(new String[0]));

      int i = 0;
      for (String tagValue : values) {
        urlStringBuilder.append(tagUrl);
        urlStringBuilder.append(ProxyConstants.CODE_URL_VALUE_SEPARATOR);
        urlStringBuilder.append(tagValue);

        if (i != len - 1) {
          urlStringBuilder.append(ProxyConstants.PARAM_VALUES_SEPARATOR);
        }
        i++;
      }
    }
  }

  private boolean isSyncUrl(ServletRequestDetails servletRequestDetails) {
    if (servletRequestDetails.getRequestType() == RequestTypeEnum.GET
        && !TextUtils.isEmpty(servletRequestDetails.getResourceName())) {
      String requestPath = servletRequestDetails.getRequestPath();
      return isResourceTypeRequest(
          requestPath.replace(servletRequestDetails.getFhirServerBase(), ""));
    }

    return false;
  }

  private boolean isResourceTypeRequest(String requestPath) {
    if (!TextUtils.isEmpty(requestPath)) {
      String[] sections = requestPath.split(ProxyConstants.HTTP_URL_SEPARATOR);

      return sections.length == 1 || (sections.length == 2 && TextUtils.isEmpty(sections[1]));
    }

    return false;
  }

  @VisibleForTesting
  protected IgnoredResourcesConfig getIgnoredResourcesConfigFileConfiguration(String configFile) {
    if (configFile != null && !configFile.isEmpty()) {
      try {
        Gson gson = new Gson();
        config = gson.fromJson(new FileReader(configFile), IgnoredResourcesConfig.class);
        if (config == null || config.entries == null) {
          throw new IllegalArgumentException("A map with a single `entries` array expected!");
        }
        for (IgnoredResourcesConfig entry : config.entries) {
          if (entry.getPath() == null) {
            throw new IllegalArgumentException("Allow-list entries should have a path.");
          }
        }

      } catch (IOException e) {
        logger.error("IO error while reading sync-filter skip-list config file {}", configFile);
      }
    }

    return config;
  }

  @VisibleForTesting
  protected IgnoredResourcesConfig getSkippedResourcesConfigs() {
    return getIgnoredResourcesConfigFileConfiguration(
        System.getenv(SYNC_FILTER_IGNORE_RESOURCES_FILE_ENV));
  }

  /**
   * This method checks the request to ensure the path, request type and parameters match values in
   * the hapi_sync_filter_ignored_queries configuration
   */
  private boolean shouldSkipDataFiltering(ServletRequestDetails servletRequestDetails) {
    if (config == null) return false;

    for (IgnoredResourcesConfig entry : config.entries) {

      if (!entry.getPath().equals(servletRequestDetails.getRequestPath())) {
        continue;
      }

      if (entry.getMethodType() != null
          && !entry.getMethodType().equals(servletRequestDetails.getRequestType().name())) {
        continue;
      }

      for (Map.Entry<String, Object> expectedParam : entry.getQueryParams().entrySet()) {
        String[] actualQueryValue =
            servletRequestDetails.getParameters().get(expectedParam.getKey());

        if (actualQueryValue == null) {
          return true;
        }

        if (MATCHES_ANY_VALUE.equals(expectedParam.getValue())) {
          return true;
        } else {
          if (actualQueryValue.length != 1) {
            // We currently do not support multivalued query params in skip-lists.
            return false;
          }

          if (expectedParam.getValue() instanceof List) {
            return CollectionUtils.isEqualCollection(
                (List) expectedParam.getValue(), Arrays.asList(actualQueryValue[0].split(",")));

          } else if (actualQueryValue[0].equals(expectedParam.getValue())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @VisibleForTesting
  protected void setSkippedResourcesConfig(IgnoredResourcesConfig config) {
    this.config = config;
  }

  class IgnoredResourcesConfig {
    @Getter List<IgnoredResourcesConfig> entries;
    @Getter private String path;
    @Getter private String methodType;
    @Getter private Map<String, Object> queryParams;

    @Override
    public String toString() {
      return "SkippedFilesConfig{"
          + methodType
          + " path="
          + path
          + " fhirResources="
          + Arrays.toString(queryParams.entrySet().toArray())
          + '}';
    }
  }
}
