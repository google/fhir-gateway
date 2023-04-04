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
package com.google.fhir.gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.io.Resources;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.net.URL;
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
}
