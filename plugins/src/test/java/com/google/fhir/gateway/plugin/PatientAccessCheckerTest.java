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
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.google.common.io.Resources;
import com.google.fhir.gateway.PatientFinderImp;
import com.google.fhir.gateway.interfaces.AccessChecker;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PatientAccessCheckerTest extends AccessCheckerTestBase {

  @Before
  public void setUp() throws IOException {
    when(jwtMock.getClaim(PatientAccessChecker.Factory.PATIENT_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(PATIENT_AUTHORIZED);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
  }

  @Override
  protected AccessChecker getInstance() {
    return new PatientAccessChecker.Factory()
        .create(jwtMock, null, fhirContext, PatientFinderImp.getInstance(fhirContext));
  }

  @Test
  public void canAccessPostPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPutPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutUnauthorizedPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_NON_AUTHORIZED_ID);
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundleGetNonPatientUnauthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_get_non_patient_unauthorized.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundlePostPatientUnAuthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_post_patient.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundleDeletePatientUnAuthorized() throws IOException {
    // Query: POST / -d @bundle_transaction_delete_patient.json
    setUpFhirBundle("bundle_transaction_delete_patient.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
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
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessDeletePatientUnauthorized() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);
    AccessChecker testInstance = getInstance();

    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessDeleteObservationAuthorized() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);
    when(requestMock.getParameters())
        .thenReturn(Map.of("subject", new String[] {PATIENT_AUTHORIZED}));
    AccessChecker testInstance = getInstance();

    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessDeleteObservationUnauthorized() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);
    when(requestMock.getParameters())
        .thenReturn(Map.of("subject", new String[] {PATIENT_NON_AUTHORIZED}));
    AccessChecker testInstance = getInstance();

    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
}
