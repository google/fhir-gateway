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

import com.google.fhir.gateway.GenericFhirClient.GenericFhirClientBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.Header;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GenericFhirClientTest {

  @Test(expected = IllegalArgumentException.class)
  public void buildGenericFhirClientFhirStoreNotSetTest() {
    new GenericFhirClientBuilder().build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildGenericFhirClientNoFhirStoreBlankTest() {
    new GenericFhirClientBuilder().setFhirStore("    ").build();
  }

  @Test
  public void getAuthHeaderNoUsernamePasswordTest() {
    GenericFhirClient genericFhirClient =
        new GenericFhirClientBuilder().setFhirStore("random.fhir").build();
    Header header = genericFhirClient.getAuthHeader();
    assertThat(header.getName(), equalTo("Authorization"));
    assertThat(header.getValue(), equalTo(""));
  }

  @Test
  public void getUriForResourceTest() throws URISyntaxException {
    GenericFhirClient genericFhirClient =
        new GenericFhirClientBuilder().setFhirStore("random.fhir").build();
    URI uri = genericFhirClient.getUriForResource("hello/world");
    assertThat(uri.toString(), equalTo("random.fhir/hello/world"));
  }
}
