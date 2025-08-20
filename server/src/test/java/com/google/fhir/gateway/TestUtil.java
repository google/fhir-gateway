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

import ca.uhn.fhir.context.FhirContext;
import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.instance.model.api.IBaseResource;

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

  public static String resourceToString(FhirContext fhirContext, IBaseResource resource) {
    return fhirContext.newJsonParser().encodeResourceToString(resource);
  }

  public static boolean isSameDate(Date date1, Date date2) {
    if (date1 == null || date2 == null) return false;

    ZoneId zoneId = ZoneId.systemDefault();

    LocalDate localDate1 = date1.toInstant().atZone(zoneId).toLocalDate();
    LocalDate localDate2 = date2.toInstant().atZone(zoneId).toLocalDate();

    return localDate1.isEqual(localDate2);
  }
}
