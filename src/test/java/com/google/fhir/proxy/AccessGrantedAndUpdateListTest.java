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

import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccessGrantedAndUpdateListTest {

  private static final String TEST_LIST_ID = "test-list";
  private static final String PATIENT = "be92a43f-de46-affa-b131-bbf9eea51140";

  @Mock private HttpFhirClient httpFhirClientMock;

  @Mock private RequestDetails requestMock;

  @Mock private HttpResponse responseMock;

  private static final FhirContext fhirContext = FhirContext.forR4();

  private AccessGrantedAndUpdateList testInstance;

  @Before
  public void setUp() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    URL url = Resources.getResource("test_patient.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    TestUtil.setUpFhirResponseMock(responseMock, testJson);
  }

  @Test
  public void postProcessNewPatientPut() throws IOException {
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    testInstance =
        new AccessGrantedAndUpdateList(TEST_LIST_ID, httpFhirClientMock, fhirContext, PATIENT);
    testInstance.postProcess(responseMock, requestMock);
  }

  @Test
  public void postProcessNewPatientPost() throws IOException {
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    testInstance =
        new AccessGrantedAndUpdateList(TEST_LIST_ID, httpFhirClientMock, fhirContext, null);
    testInstance.postProcess(responseMock, requestMock);
  }
}
