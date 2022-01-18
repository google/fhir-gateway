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

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ListAccessCheckerTest extends AccessCheckerTestBase {

  private static final String TEST_LIST_ID = "test-list";

  @Mock private HttpFhirClient httpFhirClientMock;

  private void setUpFhirListSearchMock(String itemParam, String resourceFileToReturn)
      throws IOException {
    URL listUrl = Resources.getResource(resourceFileToReturn);
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    when(httpFhirClientMock.getResource(
            String.format("/List?_id=%s&item=%s&_elements=id", TEST_LIST_ID, itemParam)))
        .thenReturn(fhirResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
  }

  @Before
  public void setUp() throws IOException {
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(jwtMock.getClaim(ListAccessChecker.Factory.PATIENT_LIST_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(TEST_LIST_ID);
    setUpFhirListSearchMock("Patient/" + PATIENT_AUTHORIZED, "bundle_list_patient_item.json");
    setUpFhirListSearchMock("Patient/" + PATIENT_NON_AUTHORIZED, "bundle_empty.json");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
  }

  @Override
  protected AccessChecker getInstance(RestfulServer server) {
    return new ListAccessChecker.Factory(server).create(jwtMock, httpFhirClientMock);
  }

  @Test
  public void canAccessList() {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", TEST_LIST_ID));
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessListNotAuthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", "wrong-id"));
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  @Override
  public void canAccessPostObservationWithPerformer() throws IOException {
    setUpFhirListSearchMock(
        String.format(
            "Patient/test-patient-2,Patient/test-patient-1,Patient/%s", PATIENT_AUTHORIZED),
        "bundle_list_patient_item.json");
    super.canAccessPostObservationWithPerformer();
  }

  @Test
  public void canAccessPostPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutExistingPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("patient_id_search_single.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutNewPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("bundle_empty.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  // TODO add an Appointment POST
}
