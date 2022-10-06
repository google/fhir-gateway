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
package com.google.fhir.proxy.plugin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.io.Resources;
import com.google.fhir.proxy.ResourceFinderImp;
import com.google.fhir.proxy.interfaces.AccessChecker;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.Enumerations;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermissionAccessCheckerTest {
  static final String PATIENT_AUTHORIZED = "be92a43f-de46-affa-b131-bbf9eea51140";
  static final String PATIENT_NON_AUTHORIZED = "patient-non-authorized";
  static final IdDt PATIENT_AUTHORIZED_ID = new IdDt("Patient", PATIENT_AUTHORIZED);
  static final IdDt PATIENT_NON_AUTHORIZED_ID = new IdDt("Patient", PATIENT_NON_AUTHORIZED);

  @Mock protected DecodedJWT jwtMock;

  @Mock protected Claim claimMock;

  // TODO consider making a real request object from a URL string to avoid over-mocking.
  @Mock protected RequestDetailsReader requestMock;

  // Note this is an expensive class to instantiate, so we only do this once for all tests.
  protected static final FhirContext fhirContext = FhirContext.forR4();

  void setUpFhirBundle(String filename) throws IOException {
    when(requestMock.getResourceName()).thenReturn(null);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    URL url = Resources.getResource(filename);
    byte[] obsBytes = Resources.toByteArray(url);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
  }

  @Before
  public void setUp() {
    when(jwtMock.getClaim(PermissionAccessChecker.Factory.REALM_ACCESS_CLAIM))
        .thenReturn(claimMock);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
  }

  protected AccessChecker getInstance() {
    return new PermissionAccessChecker.Factory()
        .create(jwtMock, null, fhirContext, ResourceFinderImp.getInstance(fhirContext));
  }

  @Test
  public void testManagePatientRoleCanAccessGetPatient() throws IOException {
    // Query: GET/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("MANAGE_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);

    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(true));
  }

  @Test
  public void testGetPatientRoleCanAccessGetPatient() throws IOException {
    // Query: GET/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("GET_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(true));
  }

  @Test
  public void testGetPatientWithoutRoleCannotAccessGetPatient() throws IOException {
    // Query: GET/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList(""));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(false));
  }

  @Test
  public void testDeletePatientRoleCanAccessDeletePatient() throws IOException {
    // Query: DELETE/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("DELETE_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(true));
  }

  @Test
  public void testManagePatientRoleCanAccessDeletePatient() throws IOException {
    // Query: DELETE/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("MANAGE_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(true));
  }

  @Test
  public void testDeletePatientWithoutRoleCannotAccessDeletePatient() throws IOException {
    // Query: DELETE/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList(""));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

    AccessChecker testInstance = getInstance();
    boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

    assertThat(canAccess, equalTo(false));
  }

  @Test
  public void testPutWithManagePatientRoleCanAccessPutPatient() throws IOException {
    // Query: PUT/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("MANAGE_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void testPutPatientWithRoleCanAccessPutPatient() throws IOException {
    // Query: PUT/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("PUT_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void testPutPatientWithoutRoleCannotAccessPutPatient() throws IOException {
    // Query: PUT/PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList(""));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void testPutPatientWithDifferentIdCannotAccessPutPatient() throws IOException {
    // Query: PUT/WRONG_PID
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("PUT_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_NON_AUTHORIZED_ID);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void testPostPatientWithRoleCanAccessPostPatient() throws IOException {
    // Query: /POST
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList("POST_PATIENT"));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void testPostPatientWithoutRoleCannotAccessPostPatient() throws IOException {
    // Query: /POST
    setUpFhirBundle("test_patient.json");

    Map<String, Object> map = new HashMap<>();
    map.put("roles", Arrays.asList(""));
    when(claimMock.asMap()).thenReturn(map);
    when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

    AccessChecker testInstance = getInstance();
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
}
