/*
 * Copyright 2021-2024 Google LLC
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
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.IRestfulResponse;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRestfulResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class BearerAuthorizationInterceptorTest {

  private static final FhirContext fhirContext = FhirContext.forR4();

  private BearerAuthorizationInterceptor testInstance;

  private static final String BASE_URL = "http://myproxy/fhir";
  private static final String FHIR_STORE =
      "https://healthcare.googleapis.com/v1/projects/fhir-sdk/locations/us/datasets/"
          + "synthea-sample-data/fhirStores/gcs-data/fhir";
  @Mock private HttpFhirClient fhirClientMock;

  @Mock private RestfulServer serverMock;

  @Mock private TokenVerifier tokenVerifierMock;

  @Mock private ServletRequestDetails requestMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private HttpResponse fhirResponseMock;

  private final Writer writerStub = new StringWriter();

  private BearerAuthorizationInterceptor createTestInstance(
      boolean isAccessGranted, String allowedQueriesConfig) throws IOException {
    return new BearerAuthorizationInterceptor(
        fhirClientMock,
        tokenVerifierMock,
        serverMock,
        (jwt, httpFhirClient, fhirContext, patientFinder) ->
            new AccessChecker() {
              @Override
              public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
                return new NoOpAccessDecision(isAccessGranted);
              }
            },
        new AllowedQueriesChecker(allowedQueriesConfig));
  }

  @Before
  public void setUp() throws IOException {
    when(serverMock.getServerBaseForRequest(any(ServletRequestDetails.class))).thenReturn(BASE_URL);
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(fhirClientMock.handleRequest(requestMock)).thenReturn(fhirResponseMock);
    when(fhirClientMock.getBaseUrl()).thenReturn(FHIR_STORE);
    testInstance = createTestInstance(true, null);
  }

  private void setupBearerAndFhirResponse(String fhirStoreResponse) throws IOException {
    setupFhirResponse(fhirStoreResponse, true);
  }

  private void setupFhirResponse(String fhirStoreResponse, boolean addBearer) throws IOException {
    if (addBearer) {
      when(requestMock.getHeader("Authorization")).thenReturn("Bearer ANYTHING");
    }
    IRestfulResponse proxyResponseMock = Mockito.mock(IRestfulResponse.class);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    when(proxyResponseMock.getResponseWriter(
            anyInt(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(writerStub);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, fhirStoreResponse);
  }

  @Test
  public void authorizeRequestPatient() throws IOException {
    URL patientUrl = Resources.getResource("test_patient.json");
    String testPatientJson = Resources.toString(patientUrl, StandardCharsets.UTF_8);
    setupBearerAndFhirResponse(testPatientJson);
    testInstance.authorizeRequest(requestMock);
    assertThat(testPatientJson, equalTo(writerStub.toString()));
  }

  @Test
  public void authorizeRequestList() throws IOException {
    URL patientUrl = Resources.getResource("patient-list-example.json");
    String testListJson = Resources.toString(patientUrl, StandardCharsets.UTF_8);
    setupBearerAndFhirResponse(testListJson);
    testInstance.authorizeRequest(requestMock);
    assertThat(testListJson, equalTo(writerStub.toString()));
  }

  @Test
  public void authorizeRequestTestReplaceUrl() throws IOException {
    URL searchUrl = Resources.getResource("patient_id_search.json");
    String testPatientIdSearch = Resources.toString(searchUrl, StandardCharsets.UTF_8);
    setupBearerAndFhirResponse(testPatientIdSearch);
    testInstance.authorizeRequest(requestMock);
    String replaced = testPatientIdSearch.replaceAll(FHIR_STORE, BASE_URL);
    assertThat(replaced, equalTo(writerStub.toString()));
  }

  @Test
  public void authorizeRequestTestResourceErrorResponse() throws IOException {
    URL errorUrl = Resources.getResource("error_operation_outcome.json");
    String errorResponse = Resources.toString(errorUrl, StandardCharsets.UTF_8);
    setupBearerAndFhirResponse(errorResponse);
    when(fhirResponseMock.getStatusLine().getStatusCode())
        .thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    testInstance.authorizeRequest(requestMock);
    String replaced = errorResponse.replaceAll(FHIR_STORE, BASE_URL);
    assertThat(replaced, equalTo(writerStub.toString()));
  }

  void noAuthRequestSetup(String requestPath) throws IOException {
    IRestfulResponse proxyResponseMock = Mockito.mock(IRestfulResponse.class);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    when(proxyResponseMock.getResponseWriter(
            anyInt(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(writerStub);
    when(requestMock.getRequestPath()).thenReturn(requestPath);
  }

  @Test
  public void authorizeRequestWellKnown() throws IOException {
    noAuthRequestSetup(BearerAuthorizationInterceptor.WELL_KNOWN_CONF_PATH);
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    when(requestMock.getServletRequest()).thenReturn(servletRequestMock);
    when(servletRequestMock.getProtocol()).thenReturn("HTTP/1.1");
    URL idpUrl = Resources.getResource("idp_keycloak_config.json");
    String testIdpConfig = Resources.toString(idpUrl, StandardCharsets.UTF_8);
    when(tokenVerifierMock.getWellKnownConfig()).thenReturn(testIdpConfig);

    testInstance.authorizeRequest(requestMock);
    Gson gson = new Gson();
    Map<String, Object> jsonMap = Maps.newHashMap();
    jsonMap = gson.fromJson(writerStub.toString(), jsonMap.getClass());
    assertThat(jsonMap.get("issuer"), equalTo("https://token.issuer/realms/test"));
    assertThat(
        jsonMap.get("authorization_endpoint"),
        equalTo("https://token.issuer/protocol/openid-connect/auth"));
    assertThat(
        jsonMap.get("token_endpoint"),
        equalTo("https://token.issuer/protocol/openid-connect/token"));
    assertThat(
        jsonMap.get("jwks_uri"), equalTo("https://token.issuer/protocol/openid-connect/certs"));
    assertThat(
        jsonMap.get("grant_types_supported"), equalTo(Lists.newArrayList("authorization_code")));
    assertThat(
        jsonMap.get("response_types_supported"),
        equalTo(
            Lists.newArrayList(
                "code",
                "none",
                "id_token",
                "token",
                "id_token token",
                "code id_token",
                "code token",
                "code id_token token")));
    assertThat(
        jsonMap.get("subject_types_supported"), equalTo(Lists.newArrayList("public", "pairwise")));
    assertThat(
        jsonMap.get("id_token_signing_alg_values_supported"),
        equalTo(
            Lists.newArrayList(
                "PS384", "ES384", "RS384", "HS256", "HS512", "ES256", "RS256", "HS384", "ES512",
                "PS256", "PS512", "RS512")));
    assertThat(
        jsonMap.get("code_challenge_methods_supported"), equalTo(Lists.newArrayList("S256")));
  }

  @Test
  public void authorizeRequestMetadata() throws IOException {
    noAuthRequestSetup(BearerAuthorizationInterceptor.METADATA_PATH);
    URL capabilityUrl = Resources.getResource("capability.json");
    String capabilityJson = Resources.toString(capabilityUrl, StandardCharsets.UTF_8);
    setupBearerAndFhirResponse(capabilityJson);
    testInstance.authorizeRequest(requestMock);
    IParser parser = fhirContext.newJsonParser();
    IBaseResource resource = parser.parseResource(writerStub.toString());
    assertThat(resource, instanceOf(CapabilityStatement.class));
    CapabilityStatement capability = (CapabilityStatement) resource;
    assertThat(capability.getRest().get(0).getSecurity().getCors(), equalTo(true));
    assertThat(
        capability.getRest().get(0).getSecurity().getService().get(0).getCoding().get(0).getCode(),
        equalTo("OAuth"));
  }

  @Test
  public void authorizeAllowedUnauthenticatedRequest() throws IOException {
    // Changing the access-checker to something that always denies except the allowed queries
    testInstance =
        createTestInstance(
            false, Resources.getResource("allowed_unauthenticated_queries.json").getPath());
    String responseJson = "{\"resourceType\": \"Bundle\"}";
    setupFhirResponse(responseJson, false);
    when(requestMock.getRequestPath()).thenReturn("Composition");

    testInstance.authorizeRequest(requestMock);

    assertThat(responseJson, equalTo(writerStub.toString()));
  }

  @Test(expected = ForbiddenOperationException.class)
  public void deniedRequest() throws IOException {
    // Changing the access-checker to something that always denies.
    testInstance =
        createTestInstance(
            false, Resources.getResource("allowed_unauthenticated_queries.json").getPath());
    setupBearerAndFhirResponse("never returned response");
    when(requestMock.getRequestPath()).thenReturn("Patient");

    testInstance.authorizeRequest(requestMock);
  }

  @Test
  public void mutateRequest() {
    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.addParameter("param1", new String[] {"param1-value1"});
    requestDetails.addParameter("param2", new String[] {"param2-value1"});

    HashMap<String, List<String>> paramMutations = new HashMap<>();
    paramMutations.put("param1", List.of("param1-value2"));
    paramMutations.put("param3", List.of("param3-value1", "param3-value2"));
    AccessDecision mutableAccessDecision =
        new AccessDecision() {
          public boolean canAccess() {
            return true;
          }

          public RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {
            return RequestMutation.builder().additionalQueryParams(paramMutations).build();
          }

          public String postProcess(
              RequestDetailsReader requestDetailsReader, HttpResponse response) throws IOException {
            return null;
          }
        };

    testInstance.mutateRequest(requestDetails, mutableAccessDecision);

    assertThat(
        requestDetails.getParameters().get("param1"), arrayContainingInAnyOrder("param1-value2"));
    assertThat(
        requestDetails.getParameters().get("param2"), arrayContainingInAnyOrder("param2-value1"));
    assertThat(
        requestDetails.getParameters().get("param3"),
        arrayContainingInAnyOrder("param3-value2", "param3-value1"));
  }

  @Test
  public void mutateRequestRemoveQueryParams() {

    List<String> queryParamsToRemove = new ArrayList<>();
    queryParamsToRemove.add("param1");

    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.addParameter("param1", new String[] {"param1-value1"});
    requestDetails.addParameter("param2", new String[] {"param2-value1"});

    AccessDecision mutableAccessDecision =
        new AccessDecision() {
          public boolean canAccess() {
            return true;
          }

          public RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {
            RequestMutation requestMutation = RequestMutation.builder().build();
            requestMutation.getDiscardQueryParams().addAll(queryParamsToRemove);
            return requestMutation;
          }

          public String postProcess(
              RequestDetailsReader requestDetailsReader, HttpResponse response) throws IOException {
            return null;
          }
        };
    assertThat(requestDetails.getParameters().size(), Matchers.equalTo(2));

    testInstance.mutateRequest(requestDetails, mutableAccessDecision);

    assertThat(requestDetails.getParameters().size(), Matchers.equalTo(1));

    assertThat(
        requestDetails.getParameters().get("param2"), arrayContainingInAnyOrder("param2-value1"));
  }

  @Test
  public void shouldSendGzippedResponseWhenRequested() throws IOException {
    testInstance = createTestInstance(true, null);
    String responseJson = "{\"resourceType\": \"Bundle\"}";
    when(requestMock.getHeader("Authorization")).thenReturn("Bearer ANYTHING");
    when(requestMock.getHeader("Accept-Encoding".toLowerCase())).thenReturn("gzip");

    // requestMock.getResponse() {@link ServletRequestDetails#getResponse()} is an abstraction HAPI
    // provides to access the response object which is of type ServletRestfulResponse {@link
    // ServletRestfulResponse}. Internally HAPI uses the HttpServletResponse {@link
    // HttpServletResponse} object to perform any response related operations for this wrapper class
    // ServletRestfulResponse. We have to perform mocking at two levels: one with
    // requestMock.getResponse() because this is how we access the wrapper response object and write
    // to it. We also need to perform a deeper level mock using requestMock.getServletResponse()
    // {@link ServletRequestDetails#getServletResponse()} for the internal HAPI operations to be
    // performed successfully. This complication arises from us mocking the request object. Had the
    // object been not mocked, and set by a server we would not have needed to do this levels of
    // mocks.
    when(requestMock.getServer()).thenReturn(serverMock);
    ServletRestfulResponse proxyResponseMock = new ServletRestfulResponse(requestMock);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    HttpServletResponse proxyServletResponseMock = new MockHttpServletResponse();
    when(requestMock.getServletResponse()).thenReturn(proxyServletResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, responseJson);

    testInstance.authorizeRequest(requestMock);

    assertThat(
        proxyServletResponseMock.getHeader("Content-Encoding".toLowerCase()), equalTo("gzip"));
  }

  @Test
  public void shouldSendGzippedResponseWhenRequestedCaseInsensitive() throws IOException {
    testInstance = createTestInstance(true, null);
    String responseJson = "{\"resourceType\": \"Bundle\"}";
    when(requestMock.getHeader("Authorization")).thenReturn("Bearer ANYTHING");
    when(requestMock.getHeader("Accept-Encoding".toLowerCase())).thenReturn("GZIP");
    when(requestMock.getServer()).thenReturn(serverMock);
    ServletRestfulResponse proxyResponseMock = new ServletRestfulResponse(requestMock);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    HttpServletResponse proxyServletResponseMock = new MockHttpServletResponse();
    when(requestMock.getServletResponse()).thenReturn(proxyServletResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, responseJson);

    testInstance.authorizeRequest(requestMock);

    assertThat(
        proxyServletResponseMock.getHeader("Content-Encoding".toLowerCase()), equalTo("gzip"));
  }

  @Test
  public void shouldSendGzippedResponseWhenRequestedMultipleEncodingFormats() throws IOException {
    testInstance = createTestInstance(true, null);
    String responseJson = "{\"resourceType\": \"Bundle\"}";
    when(requestMock.getHeader("Authorization")).thenReturn("Bearer ANYTHING");
    when(requestMock.getHeader("Accept-Encoding".toLowerCase())).thenReturn("gzip, deflate, br");
    when(requestMock.getServer()).thenReturn(serverMock);
    ServletRestfulResponse proxyResponseMock = new ServletRestfulResponse(requestMock);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    HttpServletResponse proxyServletResponseMock = new MockHttpServletResponse();
    when(requestMock.getServletResponse()).thenReturn(proxyServletResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, responseJson);

    testInstance.authorizeRequest(requestMock);

    assertThat(
        proxyServletResponseMock.getHeader("Content-Encoding".toLowerCase()), equalTo("gzip"));
  }
}
