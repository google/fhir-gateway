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
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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

// TODO evaluate if we can provide the API of HAPI's IGenericClient as well:
//  https://hapifhir.io/hapi-fhir/docs/client/generic_client.html
public abstract class HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpFhirClient.class);

  // The list of header names to keep in a response sent from the proxy; use lower case only.
  // Reference documentation - https://www.hl7.org/fhir/http.html#ops,
  // https://www.hl7.org/fhir/async.html
  // Note we don't copy content-length/type because we may modify the response.
  static final Set<String> RESPONSE_HEADERS_TO_KEEP =
      Sets.newHashSet(
          "last-modified",
          "date",
          "expires",
          "content-location",
          "content-encoding",
          "etag",
          "location",
          "x-progress",
          "x-request-id",
          "x-correlation-id");

  // The list of incoming header names to keep for forwarding request to the FHIR server; use lower
  // case only.
  // Reference documentation - https://www.hl7.org/fhir/http.html#ops,
  // https://www.hl7.org/fhir/async.html
  // We should NOT copy Content-Length as this is automatically set by the RequestBuilder when
  // setting content Entity; otherwise we will get a ClientProtocolException.
  // TODO(https://github.com/google/fhir-access-proxy/issues/60): Allow Accept header
  static final Set<String> REQUEST_HEADERS_TO_KEEP =
      Sets.newHashSet(
          "content-type",
          "accept-encoding",
          "last-modified",
          "etag",
          "prefer",
          "fhirVersion",
          "if-none-exist",
          "if-match",
          "if-none-match",
          // Condition header spec - https://www.rfc-editor.org/rfc/rfc7232#section-7.2
          "if-modified-since",
          "if-unmodified-since",
          "if-range",
          "x-request-id",
          "x-correlation-id",
          "x-forwarded-for",
          "x-forwarded-host");

  protected abstract String getBaseUrl();

  protected abstract URI getUriForResource(String resourcePath) throws URISyntaxException;

  protected abstract Header getAuthHeader();

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

  /** This method is intended to be used only for requests that are relayed to the FHIR store. */
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

  public HttpResponse getResource(String resourcePath) throws IOException {
    RequestBuilder requestBuilder = RequestBuilder.get();
    setUri(requestBuilder, resourcePath);
    return sendRequest(requestBuilder);
  }

  public HttpResponse patchResource(String resourcePath, String jsonPatch) throws IOException {
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

  List<Header> responseHeadersToKeep(HttpResponse response) {
    List<Header> headers = Lists.newArrayList();
    for (Header header : response.getAllHeaders()) {
      if (RESPONSE_HEADERS_TO_KEEP.contains(header.getName().toLowerCase())) {
        headers.add(header);
      }
    }
    return headers;
  }

  @VisibleForTesting
  void copyRequiredHeaders(ServletRequestDetails request, RequestBuilder builder) {
    for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
      if (REQUEST_HEADERS_TO_KEEP.contains(entry.getKey().toLowerCase())) {
        for (String value : entry.getValue()) {
          builder.addHeader(entry.getKey(), value);
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
