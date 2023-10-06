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

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenVerifier {

  private static final Logger logger = LoggerFactory.getLogger(TokenVerifier.class);
  private static final String TOKEN_ISSUER_ENV = "TOKEN_ISSUER";
  private static final String WELL_KNOWN_ENDPOINT_ENV = "WELL_KNOWN_ENDPOINT";
  private static final String WELL_KNOWN_ENDPOINT_DEFAULT = ".well-known/openid-configuration";
  private static final String BEARER_PREFIX = "Bearer ";

  // TODO: Make this configurable or based on the given JWT; we should at least support some other
  // RSA* and ES* algorithms (requires ECDSA512 JWT algorithm).
  private static final String SIGN_ALGORITHM = "RS256";

  private final String tokenIssuer;
  // Note the Verification class is _not_ thread-safe but the JWTVerifier instances created by its
  // `build()` are thread-safe and reusable. It is important to reuse those instances, otherwise
  // we may end up with a memory leak; details: https://github.com/auth0/java-jwt/issues/592
  // Access to `jwtVerifierConfig` and `verifierForIssuer` should be non-concurrent.
  private final Verification jwtVerifierConfig;
  private final Map<String, JWTVerifier> verifierForIssuer;
  private final HttpUtil httpUtil;
  private final String configJson;

  @VisibleForTesting
  TokenVerifier(String tokenIssuer, String wellKnownEndpoint, HttpUtil httpUtil)
      throws IOException {
    this.tokenIssuer = tokenIssuer;
    this.httpUtil = httpUtil;
    RSAPublicKey issuerPublicKey = fetchAndDecodePublicKey();
    jwtVerifierConfig = JWT.require(Algorithm.RSA256(issuerPublicKey, null));
    this.configJson = httpUtil.fetchWellKnownConfig(tokenIssuer, wellKnownEndpoint);
    this.verifierForIssuer = new HashMap<>();
  }

  public static TokenVerifier createFromEnvVars() throws IOException {
    String tokenIssuer = System.getenv(TOKEN_ISSUER_ENV);
    if (tokenIssuer == null) {
      throw new IllegalArgumentException(
          String.format("The environment variable %s is not set!", TOKEN_ISSUER_ENV));
    }

    String wellKnownEndpoint = System.getenv(WELL_KNOWN_ENDPOINT_ENV);
    if (wellKnownEndpoint == null) {
      wellKnownEndpoint = WELL_KNOWN_ENDPOINT_DEFAULT;
      logger.info(
          String.format(
              "The environment variable %s is not set! Using default value of %s instead ",
              WELL_KNOWN_ENDPOINT_ENV, WELL_KNOWN_ENDPOINT_DEFAULT));
    }
    return new TokenVerifier(tokenIssuer, wellKnownEndpoint, new HttpUtil());
  }

  public String getWellKnownConfig() {
    return configJson;
  }

  private RSAPublicKey fetchAndDecodePublicKey() throws IOException {
    // Preconditions.checkState(SIGN_ALGORITHM.equals("ES512"));
    Preconditions.checkState(SIGN_ALGORITHM.equals("RS256"));
    // final String keyAlgorithm = "EC";
    final String keyAlgorithm = "RSA";
    try {
      // TODO: Make sure this works for any issuer not just Keycloak; instead of this we should
      // read the metadata and choose the right endpoint for the keys.
      HttpResponse response = httpUtil.getResourceOrFail(new URI(tokenIssuer));
      JsonObject jsonObject =
          JsonParser.parseString(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
              .getAsJsonObject();
      String keyStr = jsonObject.get("public_key").getAsString();
      if (keyStr == null) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "Cannot find 'public_key' in issuer metadata.");
      }
      KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);
      EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(keyStr));
      return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error in token issuer URI " + tokenIssuer, e, AuthenticationException.class);
    } catch (NoSuchAlgorithmException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Invalid algorithm " + keyAlgorithm, e, AuthenticationException.class);
    } catch (InvalidKeySpecException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Invalid KeySpec: " + e.getMessage(), e, AuthenticationException.class);
    }
    // We should never get here, this is to keep the IDE happy!
    return null;
  }

  private synchronized JWTVerifier getJwtVerifier(String issuer) {
    if (!tokenIssuer.equals(issuer)) {
      if (FhirProxyServer.isDevMode()) {
        // If server is in DEV mode, set issuer to one from request
        logger.warn("Server run in DEV mode. Setting issuer to issuer from request.");
      } else {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger,
            String.format("The token issuer %s does not match the expected token issuer", issuer),
            AuthenticationException.class);
        return null;
      }
    }
    if (!verifierForIssuer.containsKey(issuer)) {
      verifierForIssuer.put(issuer, jwtVerifierConfig.withIssuer(issuer).build());
    }
    return verifierForIssuer.get(issuer);
  }

  @VisibleForTesting
  public DecodedJWT decodeAndVerifyBearerToken(String authHeader) {
    if (!authHeader.startsWith(BEARER_PREFIX)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "Authorization header is not a valid Bearer token!",
          AuthenticationException.class);
    }
    String bearerToken = authHeader.substring(BEARER_PREFIX.length());
    DecodedJWT jwt = null;
    try {
      jwt = JWT.decode(bearerToken);
    } catch (JWTDecodeException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Failed to decode JWT: " + e.getMessage(), e, AuthenticationException.class);
    }
    String issuer = jwt.getIssuer();
    String algorithm = jwt.getAlgorithm();
    JWTVerifier jwtVerifier = getJwtVerifier(issuer);
    logger.info(
        String.format(
            "JWT issuer is %s, audience is %s, and algorithm is %s",
            issuer, jwt.getAudience(), algorithm));

    if (!SIGN_ALGORITHM.equals(algorithm)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "Only %s signing algorithm is supported, got %s", SIGN_ALGORITHM, algorithm),
          AuthenticationException.class);
    }
    DecodedJWT verifiedJwt = null;
    try {
      verifiedJwt = jwtVerifier.verify(jwt);
    } catch (JWTVerificationException e) {
      // Throwing an AuthenticationException instead since it is handled by HAPI and a 401
      // status code is returned in the response.
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format("JWT verification failed with error: %s", e.getMessage()),
          e,
          AuthenticationException.class);
    }
    return verifiedJwt;
  }
}
