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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import com.google.common.io.Resources;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.IdType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FhirUtilTest {

  private static final FhirContext fhirContext = FhirContext.forR4();

  @Test
  public void isValidIdPass() {
    assertThat(FhirUtil.isValidId("simple-id"), equalTo(true));
  }

  @Test
  public void isValidIdDotPass() {
    assertThat(FhirUtil.isValidId("id.with.dots"), equalTo(true));
  }

  @Test
  public void isValidIdUnderscoreFails() {
    assertThat(FhirUtil.isValidId("id_with_underscore"), equalTo(false));
  }

  @Test
  public void isValidIdPercentFails() {
    assertThat(FhirUtil.isValidId("id-with-%"), equalTo(false));
  }

  @Test
  public void isValidIdWithNumbersPass() {
    assertThat(FhirUtil.isValidId("id-with-numbers-892-12"), equalTo(true));
  }

  @Test
  public void isValidIdSlashFails() {
    assertThat(FhirUtil.isValidId("id-with-//"), equalTo(false));
  }

  @Test
  public void isValidIdTooShort() {
    assertThat(FhirUtil.isValidId(""), equalTo(false));
  }

  @Test
  public void isValidIdTooLong() {
    assertThat(
        FhirUtil.isValidId(
            "too-long-id-0123456789012345678901234567890123456789012345678901234567890123456789"),
        equalTo(false));
  }

  @Test
  public void isValidIdNull() {
    assertThat(FhirUtil.isValidId(""), equalTo(false));
  }

  @Test
  public void canParseValidBundle() throws IOException {
    URL bundleUrl = Resources.getResource("patient_id_search.json");
    byte[] bundleBytes = Resources.toByteArray(bundleUrl);
    RequestDetailsReader requestMock = mock(RequestDetailsReader.class);
    when(requestMock.loadRequestContents()).thenReturn(bundleBytes);
    assertThat(
        FhirUtil.parseRequestToBundle(fhirContext, requestMock).getEntry().size(), equalTo(10));
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwExceptionOnNonBundleResource() throws IOException {
    URL bundleUrl = Resources.getResource("test_patient.json");
    byte[] bundleBytes = Resources.toByteArray(bundleUrl);
    RequestDetailsReader requestMock = mock(RequestDetailsReader.class);
    when(requestMock.loadRequestContents()).thenReturn(bundleBytes);
    FhirUtil.parseRequestToBundle(fhirContext, requestMock).getEntry().size();
  }

  @Test
  public void getRestOperationTypePatchReturnsPatch() {
    assertThat(
        FhirUtil.getRestOperationType("PATCH", null, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.PATCH));
  }

  @Test
  public void getRestOperationTypePutReturnsUpdate() {
    assertThat(
        FhirUtil.getRestOperationType("PUT", null, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.UPDATE));
  }

  @Test
  public void getRestOperationTypePostReturnsCreate() {
    assertThat(
        FhirUtil.getRestOperationType("POST", null, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.CREATE));
  }

  @Test
  public void getRestOperationTypeDeleteReturnsDelete() {
    assertThat(
        FhirUtil.getRestOperationType("DELETE", null, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.DELETE));
  }

  @Test
  public void getRestOperationTypeGetWithVersionedIdReturnsVRead() {
    IdType id = new IdType("Patient/123/_history/1");
    assertThat(
        FhirUtil.getRestOperationType("GET", id, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.VREAD));
  }

  @Test
  public void getRestOperationTypeGetWithIdReturnsRead() {
    IdType id = new IdType("Patient/123");
    assertThat(
        FhirUtil.getRestOperationType("GET", id, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.READ));
  }

  @Test
  public void getRestOperationTypeGetWithPagingActionReturnsGetPage() {
    Map<String, String[]> params = new HashMap<>();
    params.put(Constants.PARAM_PAGINGACTION, new String[] {"some-test-uuid"});
    assertThat(
        FhirUtil.getRestOperationType("GET", null, params),
        equalTo(RestOperationTypeEnum.GET_PAGE));
  }

  @Test
  public void getRestOperationTypeGetWithoutIdOrPagingReturnsSearchType() {
    assertThat(
        FhirUtil.getRestOperationType("GET", null, Collections.emptyMap()),
        equalTo(RestOperationTypeEnum.SEARCH_TYPE));
  }

  @Test
  public void getRestOperationTypeUnknownOperationReturnsNull() {
    assertThat(
        FhirUtil.getRestOperationType("OPTIONS", null, Collections.emptyMap()), equalTo(null));
  }
}
