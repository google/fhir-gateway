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

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpFhirClient.class);

  protected abstract String getBaseUrl();

  protected abstract URI getUriForResource(String resourcePath) throws URISyntaxException;

  protected abstract Header getAuthHeader();

  public abstract List<Header> responseHeadersToKeep(HttpResponse response);

  private void setUri(RequestBuilder builder, String resourcePath) {
    try {
      URI uri = getUriForResource(resourcePath);
      builder.setUri(uri);
      logger.info("FHIR store resource is " + uri);
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error in building URI for resource " + resourcePath);
    }
  }

  HttpResponse handleRequest(ServletRequestDetails request) throws IOException {
    String httpMethod = request.getServletRequest().getMethod();
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    setUri(builder, request.getRequestPath());
    // TODO Check why this does not work Content-Type is application/x-www-form-urlencoded.
    byte[] requestContent = request.loadRequestContents();
    if (requestContent != null && requestContent.length > 0) {
      String contentType = request.getHeader("Content-Type");
      if (contentType == null) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "Content-Type header should be set for requests with body.");
      }
      builder.setEntity(new ByteArrayEntity(requestContent));
    }
    copyRequiredHeaders(request, builder);
    copyParameters(request, builder);
    return sendRequest(builder);
  }

  HttpResponse getResource(String resourcePath) throws IOException {
    RequestBuilder requestBuilder = RequestBuilder.get();
    setUri(requestBuilder, resourcePath);
    return sendRequest(requestBuilder);
  }

  HttpResponse patchResource(String resourcePath, String jsonPatch) throws IOException {
    Preconditions.checkArgument(jsonPatch != null && !jsonPatch.isEmpty());
    RequestBuilder requestBuilder = RequestBuilder.patch();
    setUri(requestBuilder, resourcePath);
    byte[] content = jsonPatch.getBytes(Constants.CHARSET_UTF8);
    requestBuilder.setCharset(Constants.CHARSET_UTF8);
    requestBuilder.setEntity(new ByteArrayEntity(content, ProxyConstants.JSON_PATCH_CONTENT));
    return sendRequest(requestBuilder);
  }

  private HttpResponse sendRequest(RequestBuilder builder) throws IOException {
    Preconditions.checkArgument(builder.getFirstHeader("Authorization") == null);
    Header header = getAuthHeader();
    builder.addHeader(header);
    HttpUriRequest httpRequest = builder.build();
    logger.info("Request to the FHIR store is {}", httpRequest);
    // TODO reuse if creation overhead is significant.
    HttpClient httpClient = HttpClients.createDefault();

    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(httpRequest);
    if (response.getStatusLine().getStatusCode() >= 400) {
      logger.error(
          String.format(
              "Error in FHIR resource %s method %s; status %s",
              httpRequest.getRequestLine(),
              httpRequest.getMethod(),
              response.getStatusLine().toString()));
    }
    return response;
  }

  @VisibleForTesting
  void copyRequiredHeaders(ServletRequestDetails request, RequestBuilder builder) {
    // We should NOT copy Content-Length as this is automatically set by the RequestBuilder when
    // setting content Entity; otherwise we will get a ClientProtocolException.
    Set<String> requiredHeaders = Sets.newHashSet("content-type");
    for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
      if (requiredHeaders.contains(entry.getKey().toLowerCase())) {
        for (String value : entry.getValue()) {
          builder.setHeader(entry.getKey(), value);
        }
      }
    }
  }

  @VisibleForTesting
  void copyParameters(ServletRequestDetails request, RequestBuilder builder) {
    // TODO Check if we can directly do this by copying
    // request.getServletRequest().getQueryString().
    for (Map.Entry<String, String[]> entry : request.getParameters().entrySet()) {
      for (String val : entry.getValue()) {
        builder.addParameter(entry.getKey(), val);
      }
    }
  }
}
