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

import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.entity.ContentType;

public class ProxyConstants {

  public static final String CARE_TEAM_TAG_URL = "https://smartregister.org/care-team-tag-id";

  public static final String LOCATION_TAG_URL = "https://smartregister.org/location-tag-id";

  public static final String ORGANISATION_TAG_URL =
      "https://smartregister.org/organisation-tag-id";

  public static final String TAG_SEARCH_PARAM = "_tag";

  public static final String PARAM_VALUES_SEPARATOR = ",";

  public static final String CODE_URL_VALUE_SEPARATOR = "|";

  public static final String HTTP_URL_SEPARATOR = "/";

  // Note we should not set charset here; otherwise GCP FHIR store complains about Content-Type.
  static final ContentType JSON_PATCH_CONTENT = ContentType.create(Constants.CT_JSON_PATCH);
  public static final String SYNC_STRATEGY = "syncStrategy";
  public static final String REALM_ACCESS = "realm_access";

  public interface Literals {
    String EQUALS = "=";
  }
}
