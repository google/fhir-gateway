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
package com.google.fhir.gateway.plugin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.common.io.Resources;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.PatientFinderImp;
import com.google.fhir.gateway.interfaces.AccessChecker;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ListAccessCheckerTest extends AccessCheckerTestBase {

  private static final String TEST_LIST_ID = "test-list";
  private static final String PATIENT_IN_BUNDLE_1 = "420e791b-e419-c19b-3144-29e101c2c12f";
  private static final String PATIENT_IN_BUNDLE_2 = "db6e42c7-04fc-4d9d-b394-9ff33a41e178";

  @Mock private HttpFhirClient httpFhirClientMock;

  private void setUpFhirListSearchMock(String itemParam, String resourceFileToReturn)
      throws IOException {
    URL listUrl = Resources.getResource(resourceFileToReturn);
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(httpFhirClientMock.getResource(
            String.format("/List?_id=%s&_elements=id&%s", TEST_LIST_ID, itemParam)))
        .thenReturn(fhirResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
  }

  private void setUpPatientSearchMock(String patientParam, String resourceFileToReturn)
      throws IOException {
    URL listUrl = Resources.getResource(resourceFileToReturn);
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
    doReturn(fhirResponseMock)
        .when(httpFhirClientMock)
        .getResource(String.format("/Patient?_id=%s&_elements=id", patientParam));
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
  }

  @Before
  public void setUp() throws IOException {
    when(jwtMock.getClaim(ListAccessChecker.Factory.PATIENT_LIST_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(TEST_LIST_ID);
    setUpFhirListSearchMock(
        "item=Patient%2F" + PATIENT_AUTHORIZED, "bundle_list_patient_item.json");
    setUpFhirListSearchMock("item=Patient%2F" + PATIENT_NON_AUTHORIZED, "bundle_empty.json");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
  }

  @Override
  protected AccessChecker getInstance() {
    return new ListAccessChecker.Factory()
        .create(
            jwtMock, httpFhirClientMock, fhirContext, PatientFinderImp.getInstance(fhirContext));
  }

  @Test
  public void canAccessList() {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", TEST_LIST_ID));
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessListNotAuthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", "wrong-id"));
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  @Override
  public void canAccessPostObservationWithPerformer() throws IOException {
    setUpFhirListSearchMock(
        String.format(
            "item=Patient%%2Ftest-patient-2%%2CPatient%%2Ftest-patient-1%%2CPatient%%2F%s",
            PATIENT_AUTHORIZED),
        "bundle_list_patient_item.json");
    super.canAccessPostObservationWithPerformer();
  }

  @Test
  public void canAccessPostPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutExistingPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("patient_id_search_single.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutNewPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("bundle_empty.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundleGetNonPatientUnAuthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_get_non_patient_unauthorized.json");
    setUpFhirListSearchMock(
        "item=Patient%2Fdb6e42c7-04fc-4d9d-b394-9ff33a41e178", "bundle_empty.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test(expected = InvalidRequestException.class)
  public void canAccessBundleGetPatientNonAuthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_get_patient_unauthorized.json");
    setUpFhirListSearchMock(
        "item=Patient%2Fdb6e42c7-04fc-4d9d-b394-9ff33a41e178", "bundle_empty.json");
    AccessChecker testInstance = getInstance();
    testInstance.checkAccess(requestMock).canAccess();
  }

  @Test
  public void canAccessBundlePutExistingPatient() throws IOException {
    setUpFhirBundle("bundle_transaction_put_patient.json");
    setUpPatientSearchMock(PATIENT_AUTHORIZED, "patient_id_search_single.json");
    setUpPatientSearchMock(PATIENT_IN_BUNDLE_1, "patient_id_search_single.json");
    setUpFhirListSearchMock(
        String.format(
            "item=Patient%%2F%s&item=Patient%%2F%s", PATIENT_IN_BUNDLE_1, PATIENT_AUTHORIZED),
        "bundle_list_patient_item.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundlePutNewPatient() throws IOException {
    setUpFhirBundle("bundle_transaction_put_patient.json");
    setUpPatientSearchMock(PATIENT_AUTHORIZED, "bundle_empty.json");
    setUpPatientSearchMock(PATIENT_IN_BUNDLE_1, "patient_id_search_single.json");
    setUpFhirListSearchMock(
        String.format("item=Patient%%2F%s", PATIENT_IN_BUNDLE_1), "bundle_list_patient_item.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundlePutExistingPatientUnauthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_put_unauthorized.json");
    setUpFhirListSearchMock(
        String.format("item=Patient%%2F%s", PATIENT_NON_AUTHORIZED), "bundle_empty.json");
    setUpPatientSearchMock(PATIENT_NON_AUTHORIZED, "bundle_list_patient_item.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test(expected = InvalidRequestException.class)
  public void canAccessBundleNonPatientResourcesUnauthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_no_patient_in_url.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundleNonPatientResourcesAndNewPatient() throws IOException {
    setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");
    setUpFhirListSearchMock(
        String.format(
            "item=Patient%%2F%s&item=Patient%%2F%s%%2CPatient%%2F%s",
            PATIENT_IN_BUNDLE_1, PATIENT_IN_BUNDLE_1, PATIENT_AUTHORIZED),
        "bundle_list_patient_item.json");
    setUpPatientSearchMock(PATIENT_IN_BUNDLE_2, "bundle_empty.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundlePatchUnauthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_patch_unauthorized.json");
    setUpPatientSearchMock(PATIENT_AUTHORIZED, "bundle_list_patient_item.json");
    setUpFhirListSearchMock(
        String.format(
            "item=Patient%%2Fmichael%%2CPatient%%2Fbob&item=Patient%%2F%s&item=Patient%%2F%s",
            PATIENT_IN_BUNDLE_1, PATIENT_AUTHORIZED),
        "bundle_empty.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundlePostPatient() throws IOException {
    setUpFhirBundle("bundle_transaction_post_patient.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPatchObservationUnauthorizedPatient() throws IOException {
    // Query: PATCH /Observation?subject=Patient/PATIENT_AUTHORIZED -d \
    // @test_obs_patch_unauthorized_patient.json
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getParameters())
        .thenReturn(Map.of("subject", new String[] {"be92a43f-de46-affa-b131-bbf9eea51140"}));
    URL listUrl = Resources.getResource("test_obs_patch_unauthorized_patient.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PATCH);
    AccessChecker testInstance = getInstance();
    setUpFhirListSearchMock(
        "item=Patient%2Fmichael%2CPatient%2Fbob&item=Patient%2F" + PATIENT_AUTHORIZED,
        "bundle_empty.json");
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
  // TODO add an Appointment POST
}
