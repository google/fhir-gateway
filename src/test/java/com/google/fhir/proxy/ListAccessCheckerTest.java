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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ListAccessCheckerTest {

  private static final String TEST_LIST_ID = "test-list";
  private static final String PATIENT_AUTHORIZED = "be92a43f-de46-affa-b131-bbf9eea51140";
  private static final String PATIENT_NON_AUTHORIZED = "patient-non-authorized";
  private static final IdDt PATIENT_AUTHORIZED_ID = new IdDt("Patient", PATIENT_AUTHORIZED);
  private static final IdDt PATIENT_NON_AUTHORIZED_ID = new IdDt("Patient", PATIENT_NON_AUTHORIZED);

  @Mock private RestfulServer serverMock;

  @Mock private DecodedJWT jwtMock;

  @Mock private Claim claimMock;

  @Mock private HttpFhirClient httpFhirClientMock;

  @Mock private RequestDetails requestMock;

  // Note this is an expensive class to instantiate so we only do this once for all tests.
  private final FhirContext fhirContext = FhirContext.forR4();

  private String testListJson;

  private ListAccessChecker.Factory testFactoryInstance;

  @Before
  public void setUp() throws IOException {
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(jwtMock.getClaim(ListAccessChecker.PATIENT_LIST_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(TEST_LIST_ID);
    URL listUrl = Resources.getResource("bundle_list_patient_item.json");
    testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    when(httpFhirClientMock.getResource(
            String.format(
                "/List?_id=%s&item=Patient/%s&_elements=id", TEST_LIST_ID, PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
    testFactoryInstance = new ListAccessChecker.Factory(serverMock);
  }

  @Test
  public void createTest() {
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
  }

  @Test
  public void canAccessTest() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.canAccess(requestMock), equalTo(true));
  }

  @Test
  public void canAccessNotAuthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getId()).thenReturn(PATIENT_NON_AUTHORIZED_ID);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    when(httpFhirClientMock.getResource(
            String.format(
                "/List?_id=%s&item=Patient/%s&_elements=id", TEST_LIST_ID, PATIENT_NON_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    URL listUrl = Resources.getResource("bundle_empty.json");
    String emptyListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, emptyListJson);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.canAccess(requestMock), equalTo(false));
  }

  @Test
  public void canAccessList() throws IOException {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", TEST_LIST_ID));
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.canAccess(requestMock), equalTo(true));
  }

  @Test
  public void canAccessSearchQuery() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    Map<String, String[]> params = Maps.newHashMap();
    params.put("subject", new String[] {PATIENT_AUTHORIZED});
    when(requestMock.getParameters()).thenReturn(params);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.canAccess(requestMock), equalTo(true));
  }

  @Test
  public void canAccessSearchQueryNotAuthorized() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.canAccess(requestMock), equalTo(false));
  }

  // TODO add tests for PUT with content.

}
