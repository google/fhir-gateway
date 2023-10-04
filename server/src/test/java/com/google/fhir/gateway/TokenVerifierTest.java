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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import java.io.IOException;
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
import org.apache.http.HttpResponse;
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
public class TokenVerifierTest {

  private static final Logger logger = LoggerFactory.getLogger(TokenVerifierTest.class);
  private static final String TOKEN_ISSUER = "https://token.issuer";

  @Mock private HttpUtil httpUtilMock;
  private KeyPair keyPair;
  private String testIdpConfig;
  private TokenVerifier testInstance;

  private String generateKeyPairAndEncode() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
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
    when(httpUtilMock.getResourceOrFail(any(URI.class))).thenReturn(responseMock);
    TestUtil.setUpFhirResponseMock(
        responseMock, String.format("{public_key: '%s'}", publicKeyBase64));
    URL idpUrl = Resources.getResource("idp_keycloak_config.json");
    testIdpConfig = Resources.toString(idpUrl, StandardCharsets.UTF_8);
    when(httpUtilMock.fetchWellKnownConfig(anyString(), anyString())).thenReturn(testIdpConfig);
    testInstance = new TokenVerifier(TOKEN_ISSUER, "test", httpUtilMock);
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

  @Test
  public void getWellKnownConfigTest() {
    String config = testInstance.getWellKnownConfig();
    assertThat(config, equalTo(testIdpConfig));
  }
}
