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

import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.entity.ContentType;

public class ProxyConstants {

  // Note we should not set charset here; otherwise GCP FHIR store complains about Content-Type.
  static final ContentType JSON_PATCH_CONTENT = ContentType.create(Constants.CT_JSON_PATCH);
}
