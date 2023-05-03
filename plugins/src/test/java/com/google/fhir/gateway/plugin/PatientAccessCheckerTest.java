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
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.io.Resources;
import com.google.fhir.gateway.PatientFinderImp;
import com.google.fhir.gateway.interfaces.AccessChecker;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PatientAccessCheckerTest extends AccessCheckerTestBase {

  @Mock protected Claim scopeClaimMock;

  static final String DEFAULT_TEST_SCOPES_CLAIM = "patient/*.*";

  @Before
  public void setUp() throws IOException {
    when(jwtMock.getClaim(PatientAccessChecker.Factory.PATIENT_CLAIM)).thenReturn(claimMock);
    when(jwtMock.getClaim(PatientAccessChecker.Factory.SCOPES_CLAIM)).thenReturn(scopeClaimMock);
    when(claimMock.asString()).thenReturn(PATIENT_AUTHORIZED);
    when(scopeClaimMock.asString()).thenReturn(DEFAULT_TEST_SCOPES_CLAIM);
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
  public void canAccessPatchObservationNoValidPermissionForPatient() throws IOException {
    // Query: PATCH /Observation?subject=Patient/PATIENT_AUTHORIZED -d @test_obs_patch.json
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getParameters())
        .thenReturn(Map.of("subject", new String[] {"be92a43f-de46-affa-b131-bbf9eea51140"}));
    URL listUrl = Resources.getResource("test_obs_patch.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PATCH);
    when(scopeClaimMock.asString()).thenReturn("patient/Patient.write patient/Observation.read");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test(expected = InvalidRequestException.class)
  public void canAccessPutObservationInvalidPermissionScope() {
    // Query: PUT /Observation -d @test_obs_unauthorized.json
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(scopeClaimMock.asString()).thenReturn("patient/Observation.invalid");
    AccessChecker testInstance = getInstance();
    testInstance.checkAccess(requestMock).canAccess();
  }

  @Test
  public void canAccessBundlePutPatient() throws IOException {
    setUpFhirBundle("bundle_transaction_put_authorized_patient.json");
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

  @Test
  public void canAccessBundlePutPatientNoValidPermission() throws IOException {
    setUpFhirBundle("bundle_transaction_put_authorized_patient.json");
    when(scopeClaimMock.asString()).thenReturn("patient/Observation.*");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundlePatchResources() throws IOException {
    setUpFhirBundle("bundle_transaction_patch_authorized.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundlePatchResourcesNoValidPermission() throws IOException {
    setUpFhirBundle("bundle_transaction_patch_authorized.json");
    when(scopeClaimMock.asString()).thenReturn("patient/Observation.*");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundleSearchResources() throws IOException {
    setUpFhirBundle("bundle_transaction_get_non_patient_authorized.json");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessBundleSearchResourcesNoValidPermission() throws IOException {
    setUpFhirBundle("bundle_transaction_get_non_patient_authorized.json");
    when(scopeClaimMock.asString()).thenReturn("patient/Observation.*");
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessGetObservationMultipleSubjectsUnauthorized() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    when(requestMock.getParameters())
        .thenReturn(
            Map.of("subject", new String[] {PATIENT_AUTHORIZED + "," + PATIENT_NON_AUTHORIZED}));
    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
}
