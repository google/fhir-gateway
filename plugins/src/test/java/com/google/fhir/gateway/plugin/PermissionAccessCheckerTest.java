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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.io.Resources;
import com.google.fhir.gateway.PatientFinderImp;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.Enumerations;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Ignore
public class PermissionAccessCheckerTest {

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
    public void setUp() throws IOException {
        when(jwtMock.getClaim(PermissionAccessChecker.Factory.REALM_ACCESS_CLAIM))
                .thenReturn(claimMock);
        when(jwtMock.getClaim(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM))
                .thenReturn(claimMock);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    }

    protected AccessChecker getInstance() {
        return new PermissionAccessChecker.Factory()
                .create(jwtMock, null, fhirContext, PatientFinderImp.getInstance(fhirContext));
    }

    @Test
    public void testManagePatientRoleCanAccessGetPatient() throws IOException {
        // Query: GET/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT"));
        when(claimMock.asMap()).thenReturn(map);
        when(claimMock.asString()).thenReturn("ecbis-saa");

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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("GET_PATIENT"));
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("DELETE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
    }

    @Test
    public void testPutPatientWithRoleCanAccessPutPatient() throws IOException {
        // Query: PUT/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
    }

    @Test
    public void testPutPatientWithoutRoleCannotAccessPutPatient() throws IOException {
        // Query: PUT/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
    }

    @Test
    public void testPostPatientWithRoleCanAccessPostPatient() throws IOException {
        // Query: /POST
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("POST_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
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
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
    }

    @Test
    public void testManageResourceRoleCanAccessBundlePutResources() throws IOException {
        setUpFhirBundle("bundle_transaction_put_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testPutResourceRoleCanAccessBundlePutResources() throws IOException {
        setUpFhirBundle("bundle_transaction_put_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testDeleteResourceRoleCanAccessBundleDeleteResources() throws IOException {
        setUpFhirBundle("bundle_transaction_delete.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("DELETE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testWithCorrectRolesCanAccessDifferentTypeBundleResources() throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("PUT_PATIENT", "PUT_OBSERVATION", "PUT_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testManageResourcesCanAccessDifferentTypeBundleResources() throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("MANAGE_PATIENT", "MANAGE_OBSERVATION", "MANAGE_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testManageResourcesWithMissingRoleCannotAccessDifferentTypeBundleResources()
            throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT", "MANAGE_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }

    @Test(expected = InvalidRequestException.class)
    public void testBundleResourceNonTransactionTypeThrowsException() throws IOException {
        setUpFhirBundle("bundle_empty.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList());
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        AccessChecker testInstance = getInstance();
        Assert.assertFalse(testInstance.checkAccess(requestMock).canAccess());
    }

    @Test
    public void testAccessGrantedWhenManageResourcePresentForTypeBundleResources()
            throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testAccessGrantedWhenAllRolesPresentForTypeBundleResources() throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("PUT_PATIENT", "POST_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testAccessDeniedWhenSingleRoleMissingForTypeBundleResources() throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, Arrays.asList("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }
}
