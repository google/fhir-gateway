/*
 * Copyright 2021 Google LLC
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtil {

  private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

  static void validateResponseOrFail(HttpResponse response, String resource) {
    validateResponseOrFail(response, resource, false);
  }

  static void validateResponseEntityOrFail(HttpResponse response, String resource) {
    validateResponseOrFail(response, resource, true);
  }

  private static void validateResponseOrFail(
      HttpResponse response, String resource, boolean checkEntity) {
    // All success codes are valid.
    boolean isValid =
        (response.getStatusLine().getStatusCode() >= 200
            && response.getStatusLine().getStatusCode() < 300);
    if (checkEntity) {
      isValid = isValid && (response.getEntity() != null);
    }
    if (!isValid) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "Error accessing resource %s; status %s",
              resource, response.getStatusLine().toString()));
    }
  }

  HttpResponse getResourceOrFail(URI uri) throws IOException {
    HttpClient httpClient = HttpClients.createDefault();
    HttpUriRequest request =
        RequestBuilder.get()
            .setUri(uri)
            .addHeader("Accept-Charset", StandardCharsets.UTF_8.name())
            // .addHeader("Accept", "application/fhir+json; charset=utf-8")
            .build();
    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(request);
    validateResponseOrFail(response, uri.toString());
    return response;
  }
}
