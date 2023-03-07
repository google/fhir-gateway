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

import ca.uhn.fhir.util.UrlUtil;
import com.google.fhir.gateway.interfaces.UrlDetailsFinder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Reference;

/** Class to implement the {@link UrlDetailsFinder} interface for a BundleEntryRequest URL */
public class BundleEntryRequestUrlDetailsFinder implements UrlDetailsFinder {

  private BundleEntryRequestComponent requestComponent;

  private URI requestUri;
  private Reference requestReference;

  public BundleEntryRequestUrlDetailsFinder(BundleEntryRequestComponent requestComponent)
      throws URISyntaxException {
    this.requestComponent = requestComponent;
    this.requestUri = new URI(requestComponent.getUrl());
    this.requestReference = new Reference(requestUri.getPath());
  }

  @Override
  public String getResourceName() {
    String resourceType = requestReference.getReferenceElement().getResourceType();
    if (resourceType != null) {
      return resourceType;
    } else {
      return requestUri.getPath();
    }
  }

  @Override
  public String getResourceId() {
    String resourceType = requestReference.getReferenceElement().getResourceType();
    if (resourceType != null) {
      return requestReference.getReferenceElement().getIdPart();
    } else {
      return null;
    }
  }

  @Override
  public Map<String, String[]> getQueryParameters() {
    return UrlUtil.parseQueryString(requestUri.getQuery());
  }

  @Override
  public String getRequestPath() {
    return requestComponent.getUrl();
  }
}
