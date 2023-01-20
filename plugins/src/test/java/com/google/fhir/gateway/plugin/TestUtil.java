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

import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.mockito.Mockito;

class TestUtil {

  public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson) {
    Preconditions.checkNotNull(responseJson);
    StatusLine statusLineMock = Mockito.mock(StatusLine.class);
    StringEntity testEntity = new StringEntity(responseJson, StandardCharsets.UTF_8);
    when(fhirResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(fhirResponseMock.getEntity()).thenReturn(testEntity);
  }
}
