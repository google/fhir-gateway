/*
 * Copyright 2021-2022 Google LLC
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
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class BearerAuthorizationInterceptorTest {

  private static final Logger logger =
      LoggerFactory.getLogger(BearerAuthorizationInterceptorTest.class);

  private static final FhirContext fhirContext = FhirContext.forR4();

  private BearerAuthorizationInterceptor testInstance;

  private static final String BASE_URL = "http://myprxy/fhir";
  private static final String FHIR_STORE =
      "https://healthcare.googleapis.com/v1/projects/fhir-sdk/locations/us/datasets/"
          + "synthea-sample-data/fhirStores/gcs-data/fhir";
  private static final String TOKEN_ISSUER = "https://token.issuer";

  private KeyPair keyPair;

  @Mock private HttpFhirClient fhirClientMock;

  @Mock private RestfulServer serverMock;

  @Mock private HttpUtil httpUtilMock;

  @Mock private ServletRequestDetails requestMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private HttpResponse fhirResponseMock;

  private final Writer writerStub = new StringWriter();

  private String generateKeyPairAndEncode() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(1024);
      keyPair = generator.generateKeyPair();
      Key publicKey = keyPair.getPublic();
      Preconditions.checkState("X.509".equals(publicKey.getFormat()));
      return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    } catch (GeneralSecurityException e) {
      logger.error("error in generating keys", e);
      Preconditions.checkState(false); // We should never get here!
    }
    return null;
  }

  @Before
  public void setUp() throws IOException {
    String publicKeyBase64 = generateKeyPairAndEncode();
    HttpResponse responseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(serverMock.getServerBaseForRequest(any(ServletRequestDetails.class))).thenReturn(BASE_URL);
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(httpUtilMock.getResourceOrFail(any(URI.class))).thenReturn(responseMock);
    TestUtil.setUpFhirResponseMock(
        responseMock, String.format("{public_key: '%s'}", publicKeyBase64));
    URL idpUrl = Resources.getResource("idp_keycloak_config.json");
    String testIdpConfig = Resources.toString(idpUrl, StandardCharsets.UTF_8);
    when(httpUtilMock.fetchWellKnownConfig(anyString(), anyString())).thenReturn(testIdpConfig);
    when(fhirClientMock.handleRequest(requestMock)).thenReturn(fhirResponseMock);
    when(fhirClientMock.getBaseUrl()).thenReturn(FHIR_STORE);
    testInstance =
        new BearerAuthorizationInterceptor(
            fhirClientMock,
            TOKEN_ISSUER,
            "test",
            serverMock,
            httpUtilMock,
            new PermissiveAccessChecker.Factory(),
            new AllowedQueriesChecker(null));
  }

  private String signJwt(JWTCreator.Builder jwtBuilder) {
    Algorithm algorithm =
        Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
    String token = jwtBuilder.sign(algorithm);
    logger.debug(String.format(" The generated JWT is: %s", token));
    return token;
  }

  @Test
  public void decodeAndVerifyBearerTokenTest() {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    testInstance.decodeAndVerifyBearerToken("Bearer " + signJwt(jwtBuilder));
  }

  @Test(expected = AuthenticationException.class)
  public void decodeAndVerifyBearerTokenWrongIssuer() {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER + "WRONG");
    testInstance.decodeAndVerifyBearerToken("Bearer " + signJwt(jwtBuilder));
  }

  @Test(expected = AuthenticationException.class)
  public void decodeAndVerifyBearerTokenBadSignature() {
    // We overwrite the original `keyPair` hence the signature won't match the original public key.
    generateKeyPairAndEncode();
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    testInstance.decodeAndVerifyBearerToken("Bearer " + signJwt(jwtBuilder));
  }

  @Test(expected = AuthenticationException.class)
  public void decodeAndVerifyBearerTokenNoBearer() {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    testInstance.decodeAndVerifyBearerToken(signJwt(jwtBuilder));
  }

  @Test(expected = AuthenticationException.class)
  public void decodeAndVerifyBearerTokenMalformedBearer() {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    testInstance.decodeAndVerifyBearerToken("BearerTTT " + signJwt(jwtBuilder));
  }

  @Test(expected = AuthenticationException.class)
  public void decodeAndVerifyBearerTokenMalformedToken() {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    testInstance.decodeAndVerifyBearerToken("Bearer TTT");
  }

  private void authorizeRequestCommonSetUp(String fhirStoreResponse) throws IOException {
    JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(TOKEN_ISSUER);
    String jwt = signJwt(jwtBuilder);
    when(requestMock.getHeader("Authorization")).thenReturn("Bearer " + jwt);

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
    authorizeRequestCommonSetUp(testPatientJson);
    testInstance.authorizeRequest(requestMock);
    assertThat(testPatientJson, equalTo(writerStub.toString()));
  }

  @Test
  public void authorizeRequestList() throws IOException {
    URL patientUrl = Resources.getResource("patient-list-example.json");
    String testListJson = Resources.toString(patientUrl, StandardCharsets.UTF_8);
    authorizeRequestCommonSetUp(testListJson);
    testInstance.authorizeRequest(requestMock);
    assertThat(testListJson, equalTo(writerStub.toString()));
  }

  @Test
  public void authorizeRequestTestReplaceUrl() throws IOException {
    URL searchUrl = Resources.getResource("patient_id_search.json");
    String testPatientIdSearch = Resources.toString(searchUrl, StandardCharsets.UTF_8);
    authorizeRequestCommonSetUp(testPatientIdSearch);
    testInstance.authorizeRequest(requestMock);
    String replaced = testPatientIdSearch.replaceAll(FHIR_STORE, BASE_URL);
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
    authorizeRequestCommonSetUp(capabilityJson);
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
}
