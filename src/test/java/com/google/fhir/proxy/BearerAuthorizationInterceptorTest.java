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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.api.server.IRestfulResponse;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.base.Preconditions;
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
import org.apache.http.entity.StringEntity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class BearerAuthorizationInterceptorTest {

  private static final Logger logger =
      LoggerFactory.getLogger(BearerAuthorizationInterceptorTest.class);

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

  @Mock private HttpResponse fhirResponseMock;

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
    HttpResponse responseMock = Mockito.mock(HttpResponse.class);
    when(serverMock.getServerBaseForRequest(any(ServletRequestDetails.class))).thenReturn(BASE_URL);
    when(httpUtilMock.getResourceOrFail(any(URI.class))).thenReturn(responseMock);
    StringEntity testEntity =
        new StringEntity(
            String.format("{public_key: '%s'}", publicKeyBase64), StandardCharsets.UTF_8);
    when(responseMock.getEntity()).thenReturn(testEntity);
    when(fhirClientMock.handleRequest(requestMock)).thenReturn(fhirResponseMock);
    when(fhirClientMock.getBaseUrl()).thenReturn(FHIR_STORE);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, null);
    testInstance =
        new BearerAuthorizationInterceptor(
            fhirClientMock,
            TOKEN_ISSUER,
            serverMock,
            httpUtilMock,
            new PermissiveAccessChecker.Factory());
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

  @Test
  public void authorizeRequestWellKnow() throws IOException {
    IRestfulResponse proxyResponseMock = Mockito.mock(IRestfulResponse.class);
    when(requestMock.getResponse()).thenReturn(proxyResponseMock);
    when(proxyResponseMock.getResponseWriter(
            anyInt(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(writerStub);
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    when(requestMock.getServletRequest()).thenReturn(servletRequestMock);
    when(servletRequestMock.getProtocol()).thenReturn("HTTP/1.1");
    when(requestMock.getRequestPath())
        .thenReturn(BearerAuthorizationInterceptor.WELL_KNOWN_CONF_PATH);
    testInstance.authorizeRequest(requestMock);
    Gson gson = new Gson();
    Map<String, Object> jsonMap = Maps.newHashMap();
    jsonMap = gson.fromJson(writerStub.toString(), jsonMap.getClass());
    assertThat(
        jsonMap.get("authorization_endpoint"),
        equalTo("https://token.issuer/protocol/openid-connect/auth"));
    assertThat(
        jsonMap.get("token_endpoint"),
        equalTo("https://token.issuer/protocol/openid-connect/token"));
  }
}
