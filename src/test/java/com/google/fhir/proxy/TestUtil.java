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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.mockito.Mockito;

public class TestUtil {

  public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson)
      throws IOException {
    StatusLine statusLineMock = Mockito.mock(StatusLine.class);
    HttpEntity fhirEntityMock = Mockito.mock(HttpEntity.class);
    when(fhirResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(fhirResponseMock.getEntity()).thenReturn(fhirEntityMock);
    if (responseJson != null) {
      byte[] patientBytes = responseJson.getBytes(StandardCharsets.UTF_8);
      when(fhirEntityMock.getContent()).thenReturn(new ByteArrayInputStream(patientBytes));
      // Making this `lenient` since `getContentLength` is not called in all cases.
      lenient().when(fhirEntityMock.getContentLength()).thenReturn((long) patientBytes.length);
    }
  }
}
