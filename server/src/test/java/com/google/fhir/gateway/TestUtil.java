/*
 * Copyright 2021-2025 Google LLC
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
package com.google.fhir.gateway;

import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;

class TestUtil {

  public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson) {
    Preconditions.checkNotNull(responseJson);
    StringEntity testEntity = new StringEntity(responseJson, StandardCharsets.UTF_8);
    when(fhirResponseMock.getStatusLine().getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(fhirResponseMock.getEntity()).thenReturn(testEntity);
  }

  public static String createTestAccessToken(String jsonPayloadData) {
    String encodedHeader = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String encodedPayloadData = base64UrlEncode(jsonPayloadData);
    String signature = base64UrlEncode("dummy-signature");
    return String.format("%s.%s.%s", encodedHeader, encodedPayloadData, signature);
  }

  private static String base64UrlEncode(String input) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes());
  }
}
