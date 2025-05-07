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

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.Header;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class adds customizations needed for talking to a Generic FHIR server. */
public final class GenericFhirClient extends HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(GenericFhirClient.class);

  private final String genericFhirStore;

  private GenericFhirClient(String genericFhirStore) {
    this.genericFhirStore = genericFhirStore;
    logger.info("Initialized client for generic FHIR server: " + genericFhirStore);
  }

  @Override
  public String getBaseUrl() {
    return genericFhirStore;
  }

  @Override
  protected URI getUriForResource(String resourcePath) throws URISyntaxException {
    String uri = String.format("%s/%s", genericFhirStore, resourcePath);
    URIBuilder uriBuilder = new URIBuilder(uri);
    return uriBuilder.build();
  }

  @Override
  protected Header getAuthHeader() {
    return new BasicHeader("Authorization", "");
  }

  public static class GenericFhirClientBuilder {
    private String fhirStore;

    public GenericFhirClientBuilder setFhirStore(String fhirStore) {
      this.fhirStore = fhirStore;
      return this;
    }

    public GenericFhirClient build() {
      if (fhirStore == null || fhirStore.isBlank()) {
        throw new IllegalArgumentException("FhirStore not set!");
      }
      return new GenericFhirClient(fhirStore);
    }
  }
}
