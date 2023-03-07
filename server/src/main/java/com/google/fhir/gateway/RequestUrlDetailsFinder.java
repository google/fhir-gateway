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

import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.UrlDetailsFinder;
import java.util.Map;

/** Class to implement the {@link UrlDetailsFinder} interface for a Servlet Request */
public class RequestUrlDetailsFinder implements UrlDetailsFinder {

  private RequestDetailsReader requestDetailsReader;

  public RequestUrlDetailsFinder(RequestDetailsReader requestDetailsReader) {
    this.requestDetailsReader = requestDetailsReader;
  }

  @Override
  public String getResourceName() {
    return requestDetailsReader.getResourceName();
  }

  @Override
  public String getResourceId() {
    return FhirUtil.getIdOrNull(requestDetailsReader);
  }

  @Override
  public Map<String, String[]> getQueryParameters() {
    return requestDetailsReader.getParameters();
  }

  @Override
  public String getRequestPath() {
    return requestDetailsReader.getCompleteUrl();
  }
}
