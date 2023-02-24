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
package com.google.fhir.proxy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AllowedQueriesCheckerTest {

  // TODO find a better way to make a real request object from a URL string to avoid over-mocking.
  @Mock protected RequestDetailsReader requestMock;

  @Test
  public void validGetPagesQuery() throws IOException {
    // Query: GET ?_getpages=A_PAGE_ID
    when(requestMock.getRequestPath()).thenReturn("");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("_getpages", new String[] {"A_PAGE_ID"});
    when(requestMock.getParameters()).thenReturn(params);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void validGetPagesQueryExtraValue() throws IOException {
    // Query: GET ?_getpages=A_PAGE_ID,A_SECOND_ID
    when(requestMock.getRequestPath()).thenReturn("");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("_getpages", new String[] {"A_PAGE_ID", "A_SECOND_ID"});
    when(requestMock.getParameters()).thenReturn(params);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void validGetPagesQueryExtraParam() throws IOException {
    // Query: GET ?_getpages=A_PAGE_ID&another_param=SOMETHING
    when(requestMock.getRequestPath()).thenReturn("");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("_getpages", new String[] {"A_PAGE_ID"});
    params.put("another_param", new String[] {"SOMETHING"});
    when(requestMock.getParameters()).thenReturn(params);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void noMatchForObservationQuery() throws IOException {
    // Query: GET /Observation?_getpages=A_PAGE_ID
    when(requestMock.getRequestPath()).thenReturn("/Observation");
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test(expected = IllegalArgumentException.class)
  public void configNullPath() throws IOException {
    URL configFileUrl = Resources.getResource("no_path_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
  }

  @Test(expected = IllegalArgumentException.class)
  public void malformedConfig() throws IOException {
    URL configFileUrl = Resources.getResource("malformed_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
  }

  @Test
  public void denyGetPagesQueryExtraParam() throws IOException {
    // Query: GET ?_getpages=A_PAGE_ID&another_param=SOMETHING
    when(requestMock.getRequestPath()).thenReturn("");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("_getpages", new String[] {"A_PAGE_ID"});
    params.put("another_param", new String[] {"SOMETHING"});
    when(requestMock.getParameters()).thenReturn(params);
    URL configFileUrl = Resources.getResource("allowed_queries_with_no_extra_params.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void denyQueryWithoutRequiredParam() throws IOException {
    // Query: GET ?another_param=SOMETHING
    when(requestMock.getRequestPath()).thenReturn("");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("another_param", new String[] {"SOMETHING"});
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void validGetCompositionQuery() throws IOException {
    // Query: GET /Composition
    when(requestMock.getRequestPath()).thenReturn("/Composition");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void validGetListQueryWithSpecificPathVariableValue() throws IOException {
    // Query: PUT /List/some-value-x-anything
    when(requestMock.getRequestPath()).thenReturn("/List/some-value-x-anything");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void validGetBinaryQueryWithExpectedPathVariable() throws IOException {
    // Query: GET /Binary/1234567
    when(requestMock.getRequestPath()).thenReturn("/Binary/1234567");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void denyGetBinaryQueryWithUnexpectedPathVariable() throws IOException {
    // Query: GET /Binary/unauthorized-path-variable
    when(requestMock.getRequestPath()).thenReturn("/Binary/unauthorized-path-variable");
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void validGetPatientQueryWithExpectedGetParamsAndPathVariable() throws IOException {
    // Query: GET /Patient/8899900
    when(requestMock.getRequestPath()).thenReturn("/Patient/8899900");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    Map<String, String[]> params = Maps.newHashMap();
    params.put("_sort", new String[] {"name"});
    when(requestMock.getParameters()).thenReturn(params);
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void denyGetPatientQueryWithEmptyPathVariable() throws IOException {
    // Query: GET /Patient/
    when(requestMock.getRequestPath()).thenReturn("/Patient/");
    URL configFileUrl = Resources.getResource("hapi_page_url_allowed_queries.json");
    AllowedQueriesChecker testInstance = new AllowedQueriesChecker(configFileUrl.getPath());
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }
}
