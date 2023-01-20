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
import static org.hamcrest.Matchers.equalTo;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GcpFhirClientTest {

  private GcpFhirClient gcpFhirClient;

  private final GoogleCredentials mockCredential =
      GoogleCredentials.create(new AccessToken("complicatedCode", null));

  @Mock private HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);

  @Before
  public void setUp() throws IOException {
    gcpFhirClient = new GcpFhirClient("test", mockCredential);
    Header[] mockHeader = {
      new BasicHeader("LAST-MODIFIED", "today"),
      new BasicHeader("date", "yesterday"),
      new BasicHeader("keep", "no")
    };
    Mockito.when(fhirResponseMock.getAllHeaders()).thenReturn(mockHeader);
  }

  @Test
  public void getHeaderTest() {
    Header header = gcpFhirClient.getAuthHeader();
    assertThat(header.getElements().length, equalTo(1));
    assertThat(header.getElements()[0].getName(), equalTo("Bearer complicatedCode"));
  }

  @Test
  public void getUriForResourceTest() throws URISyntaxException {
    URI uri = gcpFhirClient.getUriForResource("hello/world");
    assertThat(uri.toString(), equalTo("test/hello/world"));
  }

  @Test
  public void responseHeadersToKeepTest() {
    List<Header> headersToKeep = gcpFhirClient.responseHeadersToKeep(fhirResponseMock);
    assertThat(headersToKeep.size(), equalTo(2));
  }
}
