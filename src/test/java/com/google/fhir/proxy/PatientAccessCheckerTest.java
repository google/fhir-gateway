/*
 * Copyright 2021-2022 Google LLC
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

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PatientAccessCheckerTest extends AccessCheckerTestBase {

  @Before
  public void setUp() throws IOException {
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(jwtMock.getClaim(PatientAccessChecker.Factory.PATIENT_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(PATIENT_AUTHORIZED);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
  }

  @Override
  protected AccessChecker getInstance(RestfulServer server) {
    return new PatientAccessChecker.Factory(server).create(jwtMock, null);
  }

  @Test
  public void canAccessPostPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPutPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutUnauthorizedPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_NON_AUTHORIZED_ID);
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundleGetNonPatientUnauthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_get_non_patient_unauthorized.json");
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessBundlePostPatientUnAuthorized() throws IOException {
    setUpFhirBundle("bundle_transaction_post_patient.json");
    AccessChecker testInstance = getInstance(serverMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
}
