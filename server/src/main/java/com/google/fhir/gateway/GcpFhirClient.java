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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import org.apache.http.Header;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class adds customizations needed for talking to a GCP FHIR store. */
public class GcpFhirClient extends HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(GcpFhirClient.class);

  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  private final GoogleCredentials credentials;
  private final String gcpFhirStore;

  public GcpFhirClient(String gcpFhirStore, GoogleCredentials credentials) throws IOException {
    // Remove trailing '/'s since proxy's base URL has no trailing '/'.
    this.gcpFhirStore = gcpFhirStore.replaceAll("/+$", "");
    this.credentials = credentials;

    logger.info("Initialized a client for GCP FHIR store: " + gcpFhirStore);
  }

  @Override
  protected String getBaseUrl() {
    return gcpFhirStore;
  }

  private String getAccessToken() {
    // TODO add support for refresh token expiration.
    try {
      credentials.refreshIfExpired();
    } catch (IOException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Cannot refresh access token due to: " + e.getMessage(), e);
    }
    AccessToken accessToken = credentials.getAccessToken();
    if (accessToken == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "Cannot get an access token!");
    }
    return accessToken.getTokenValue();
  }

  @Override
  protected URI getUriForResource(String resourcePath) throws URISyntaxException {
    String uri = String.format("%s/%s", gcpFhirStore, resourcePath);
    URIBuilder uriBuilder = new URIBuilder(uri);
    return uriBuilder.build();
  }

  @Override
  protected Header getAuthHeader() {
    String authToken = String.format("Bearer %s", getAccessToken());
    return new BasicHeader("Authorization", authToken);
  }

  public static GoogleCredentials createCredentials() throws IOException {
    return GoogleCredentials.getApplicationDefault()
        .createScoped(Collections.singleton(GcpFhirClient.CLOUD_PLATFORM_SCOPE));
  }
}
