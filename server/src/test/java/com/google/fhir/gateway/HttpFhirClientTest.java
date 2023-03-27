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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpFhirClientTest {

  @Spy private HttpFhirClient fhirClient;

  @Mock private ServletRequestDetails requestMock;

  @Mock private HttpResponse httpResponse;

  @Test
  public void copyRequiredHeaders_passAllowedHeaders_addsToRequest() {
    Map<String, List<String>> headers = new HashMap<>();
    String allowedRequestHeader = "etag";
    List<String> headerValues = List.of("test-val-1", "test-val-2");
    headers.put(allowedRequestHeader, headerValues);
    when(requestMock.getHeaders()).thenReturn(headers);
    RequestBuilder requestBuilder = RequestBuilder.create("POST");

    fhirClient.copyRequiredHeaders(requestMock, requestBuilder);

    assertThat(
        Arrays.stream(requestBuilder.getHeaders(allowedRequestHeader))
            .map(header -> (BasicHeader) header)
            .map(BasicHeader::getValue)
            .toArray(),
        arrayContainingInAnyOrder("test-val-1", "test-val-2"));
  }

  @Test
  public void copyRequiredHeaders_passNotAllowedHeaders_emptyRequestHeaders() {
    Map<String, List<String>> headers = new HashMap<>();
    String unsupportedHeader = "unsupported-header";
    headers.put(unsupportedHeader, List.of("test-val-1", "test-val-2"));
    when(requestMock.getHeaders()).thenReturn(headers);
    RequestBuilder requestBuilder = RequestBuilder.create("POST");

    fhirClient.copyRequiredHeaders(requestMock, requestBuilder);

    assertThat(requestBuilder.getHeaders(unsupportedHeader), nullValue());
  }

  @Test
  public void responseHeadersToKeep_addAllowedHeaders() {
    String allowedRequestHeader = "etag";
    Header[] headers = {
      new BasicHeader(allowedRequestHeader, "test-val-1"),
      new BasicHeader(allowedRequestHeader, "test-val-2")
    };
    when(httpResponse.getAllHeaders()).thenReturn(headers);

    List<Header> responseHeaders = fhirClient.responseHeadersToKeep(httpResponse);

    assertThat(responseHeaders, containsInAnyOrder(headers));
  }

  @Test
  public void responseHeadersToKeep_passNotAllowedHeaders_emptyRequestHeaders() {
    String unsupportedHeader = "unsupportedHeader";
    Header[] headers = {
      new BasicHeader(unsupportedHeader, "test-val-1"),
      new BasicHeader(unsupportedHeader, "test-val-2")
    };
    when(httpResponse.getAllHeaders()).thenReturn(headers);

    List<Header> responseHeaders = fhirClient.responseHeadersToKeep(httpResponse);

    assertThat(responseHeaders, empty());
  }
}
