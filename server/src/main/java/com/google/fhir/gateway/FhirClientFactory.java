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

import com.google.fhir.gateway.GenericFhirClient.GenericFhirClientBuilder;
import java.io.IOException;

/**
 * This is a helper class to create appropriate FHIR clients to talk to the configured FHIR server.
 */
public class FhirClientFactory {
  private static final String PROXY_TO_ENV = "PROXY_TO";
  private static final String BACKEND_TYPE_ENV = "BACKEND_TYPE";
  private static final String AUDIT_EVENT_LOGGING_ENABLED_ENV = "AUDIT_EVENT_LOGGING_ENABLED";

  public static HttpFhirClient createFhirClientFromEnvVars() throws IOException {
    String backendType = System.getenv(BACKEND_TYPE_ENV);
    if (backendType == null) {
      throw new IllegalArgumentException(
          String.format("The environment variable %s is not set!", BACKEND_TYPE_ENV));
    }
    String fhirStore = System.getenv(PROXY_TO_ENV);
    if (fhirStore == null) {
      throw new IllegalArgumentException(
          String.format("The environment variable %s is not set!", PROXY_TO_ENV));
    }
    return chooseHttpFhirClient(backendType, fhirStore);
  }

  private static HttpFhirClient chooseHttpFhirClient(String backendType, String fhirStore)
      throws IOException {
    // TODO add an enum if the list of special FHIR servers grow and rename HAPI to GENERIC.
    if (backendType.equals("GCP")) {
      return new GcpFhirClient(fhirStore, GcpFhirClient.createCredentials());
    }

    if (backendType.equals("HAPI")) {
      return new GenericFhirClientBuilder().setFhirStore(fhirStore).build();
    }
    throw new IllegalArgumentException(
        String.format(
            "The environment variable %s is not set to either GCP or HAPI!", BACKEND_TYPE_ENV));
  }

  public static boolean isAuditEventLoggingEnabled() {
    return Boolean.parseBoolean(System.getenv(AUDIT_EVENT_LOGGING_ENABLED_ENV));
  }
}
