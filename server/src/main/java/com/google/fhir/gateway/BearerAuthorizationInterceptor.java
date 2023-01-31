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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.IRestfulResponse;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
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
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Interceptor
public class BearerAuthorizationInterceptor {

  private static final Logger logger =
      LoggerFactory.getLogger(BearerAuthorizationInterceptor.class);

  private static final String DEFAULT_CONTENT_TYPE = "application/json; charset=UTF-8";
  private static final String BEARER_PREFIX = "Bearer ";

  // See https://hl7.org/fhir/smart-app-launch/conformance.html#using-well-known
  @VisibleForTesting static final String WELL_KNOWN_CONF_PATH = ".well-known/smart-configuration";

  // For fetching CapabilityStatement: https://www.hl7.org/fhir/http.html#capabilities
  @VisibleForTesting static final String METADATA_PATH = "metadata";

  // TODO: Make this configurable or based on the given JWT; we should at least support some other
  // RSA* and ES* algorithms (requires ECDSA512 JWT algorithm).
  private static final String SIGN_ALGORITHM = "RS256";

  private final String tokenIssuer;
  private final Verification jwtVerifierConfig;
  private final HttpUtil httpUtil;
  private final RestfulServer server;
  private final HttpFhirClient fhirClient;
  private final AccessCheckerFactory accessFactory;
  private final AllowedQueriesChecker allowedQueriesChecker;
  private final String configJson;

  BearerAuthorizationInterceptor(
      HttpFhirClient fhirClient,
      String tokenIssuer,
      String wellKnownEndpoint,
      RestfulServer server,
      HttpUtil httpUtil,
      AccessCheckerFactory accessFactory,
      AllowedQueriesChecker allowedQueriesChecker)
      throws IOException {
    Preconditions.checkNotNull(fhirClient);
    Preconditions.checkNotNull(server);
    this.server = server;
    this.fhirClient = fhirClient;
    this.httpUtil = httpUtil;
    this.tokenIssuer = tokenIssuer;
    this.accessFactory = accessFactory;
    this.allowedQueriesChecker = allowedQueriesChecker;
    RSAPublicKey issuerPublicKey = fetchAndDecodePublicKey();
    jwtVerifierConfig = JWT.require(Algorithm.RSA256(issuerPublicKey, null));
    this.configJson = httpUtil.fetchWellKnownConfig(tokenIssuer, wellKnownEndpoint);
    logger.info("Created proxy to the FHIR store " + this.fhirClient.getBaseUrl());
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

  private JWTVerifier buildJwtVerifier(String issuer) {

    if (tokenIssuer.equals(issuer)) {
      return jwtVerifierConfig.withIssuer(tokenIssuer).build();
    } else if (FhirProxyServer.isDevMode()) {
      // If server is in DEV mode, set issuer to one from request
      logger.warn("Server run in DEV mode. Setting issuer to issuer from request.");
      return jwtVerifierConfig.withIssuer(issuer).build();
    } else {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format("The token issuer %s does not match the expected token issuer", issuer),
          AuthenticationException.class);
      return null;
    }
  }

  @VisibleForTesting
  DecodedJWT decodeAndVerifyBearerToken(String authHeader) {
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
    JWTVerifier jwtVerifier = buildJwtVerifier(issuer);
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

  private AccessDecision checkAuthorization(RequestDetails requestDetails) {
    if (METADATA_PATH.equals(requestDetails.getRequestPath())) {
      // No further check is required; provide CapabilityStatement with security information.
      // Note this is potentially an expensive resource to produce because of its size and parsings.
      // Abuse of this open endpoint should be blocked by DDOS prevention means.
      return CapabilityPostProcessor.getInstance(server.getFhirContext());
    }
    // Check the Bearer token to be a valid JWT with required claims.
    String authHeader = requestDetails.getHeader("Authorization");
    if (authHeader == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "No Authorization header provided!", AuthenticationException.class);
    }
    DecodedJWT decodedJwt = decodeAndVerifyBearerToken(authHeader);
    FhirContext fhirContext = server.getFhirContext();
    RequestDetailsReader requestDetailsReader = new RequestDetailsToReader(requestDetails);
    AccessDecision allowedQueriesDecision = allowedQueriesChecker.checkAccess(requestDetailsReader);
    if (allowedQueriesDecision.canAccess()) {
      return allowedQueriesDecision;
    }
    PatientFinderImp patientFinder = PatientFinderImp.getInstance(fhirContext);
    AccessChecker accessChecker =
        accessFactory.create(decodedJwt, fhirClient, fhirContext, patientFinder, patientFinder);
    if (accessChecker == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Cannot create an AccessChecker!", AuthenticationException.class);
    }
    AccessDecision outcome = accessChecker.checkAccess(requestDetailsReader);
    if (!outcome.canAccess()) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "User is not authorized to %s %s",
              requestDetails.getRequestType(), requestDetails.getCompleteUrl()),
          ForbiddenOperationException.class);
    }
    return outcome;
  }

  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
  public boolean authorizeRequest(RequestDetails requestDetails) {
    Preconditions.checkArgument(requestDetails instanceof ServletRequestDetails);
    ServletRequestDetails servletDetails = (ServletRequestDetails) requestDetails;
    logger.info(
        "Started authorization check for {} {}",
        requestDetails.getRequestType(),
        requestDetails.getCompleteUrl());
    final String requestPath = requestDetails.getRequestPath();
    if (WELL_KNOWN_CONF_PATH.equals(requestPath)) {
      // No authorization is needed for the well-known URL.
      serveWellKnown(servletDetails);
      return false;
    }
    AccessDecision outcome = checkAuthorization(requestDetails);
    logger.debug("Authorized request path " + requestPath);
    try {
      HttpResponse response = fhirClient.handleRequest(servletDetails);
      HttpUtil.validateResponseEntityExistsOrFail(response, requestPath);
      // TODO communicate post-processing failures to the client; see:
      //   https://github.com/google/fhir-access-proxy/issues/66

      String content = null;
      if (HttpUtil.isResponseValid(response)) {
        try {
          // For post-processing rationale/example see b/207589782#comment3.
          content = outcome.postProcess(response);
        } catch (Exception e) {
          // Note this is after a successful fetch/update of the FHIR store. That success must be
          // passed to the client even if the access related post-processing fails.
          logger.error(
              "Exception in access related post-processing for {} {}",
              requestDetails.getRequestType(),
              requestDetails.getRequestPath(),
              e);
        }
      }
      HttpEntity entity = response.getEntity();
      logger.debug(String.format("The response for %s is %s ", requestPath, response));
      logger.info("FHIR store response length: " + entity.getContentLength());
      IRestfulResponse proxyResponse = requestDetails.getResponse();
      for (Header header : fhirClient.responseHeadersToKeep(response)) {
        proxyResponse.addHeader(header.getName(), header.getValue());
      }
      // This should be called after adding headers.
      // TODO handle non-text responses, e.g., gzip.
      // TODO verify DEFAULT_CONTENT_TYPE/CHARSET are compatible with `entity.getContentType()`.
      Writer writer =
          proxyResponse.getResponseWriter(
              response.getStatusLine().getStatusCode(),
              response.getStatusLine().toString(),
              DEFAULT_CONTENT_TYPE,
              Constants.CHARSET_NAME_UTF8,
              false);
      Reader reader;
      if (content != null) {
        // We can read the entity body stream only once; in this case we have already done that.
        reader = new StringReader(content);
      } else {
        reader = HttpUtil.readerFromEntity(entity);
      }
      replaceAndCopyResponse(reader, writer, server.getServerBaseForRequest(servletDetails));
    } catch (IOException e) {
      logger.error(
          String.format(
              "Exception for resource %s method %s with error: %s",
              requestPath, servletDetails.getServletRequest().getMethod(), e));
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, e.getMessage(), e);
    }

    // The request processing stops here, hence returning false.
    return false;
  }

  /**
   * Reads the content from the FHIR store response `entity`, replaces any FHIR store URLs by the
   * corresponding proxy URLs, and write the modified response to the proxy response `writer`.
   *
   * @param entityContentReader a reader for the entity content of the FHIR store response
   * @param writer the writer for proxy response
   * @param proxyBase the base URL of the proxy
   */
  private void replaceAndCopyResponse(Reader entityContentReader, Writer writer, String proxyBase)
      throws IOException {
    // To make this more efficient, this only does a string search/replace; we may need to add
    // proper URL parsing if we need to address edge cases in URL no-op changes. This string
    // matching can be done more efficiently if needed, but we should avoid loading the full
    // stream in memory.
    String fhirStoreUrl = fhirClient.getBaseUrl();
    int numMatched = 0;
    int n;
    while ((n = entityContentReader.read()) >= 0) {
      char c = (char) n;
      if (fhirStoreUrl.charAt(numMatched) == c) {
        numMatched++;
        if (numMatched == fhirStoreUrl.length()) {
          // A match found; replace it with proxy's base URL.
          writer.write(proxyBase);
          numMatched = 0;
        }
      } else {
        writer.write(fhirStoreUrl.substring(0, numMatched));
        writer.write(c);
        numMatched = 0;
      }
    }
    if (numMatched > 0) {
      // Handle any remaining characters that partially matched.
      writer.write(fhirStoreUrl.substring(0, numMatched));
    }
  }

  private void serveWellKnown(ServletRequestDetails request) {
    IRestfulResponse proxyResponse = request.getResponse();
    final String statusLine =
        String.format(
            "%s %d %s",
            request.getServletRequest().getProtocol(),
            HttpStatus.SC_OK,
            Constants.HTTP_STATUS_NAMES.get(HttpStatus.SC_OK));
    try {
      Writer writer =
          proxyResponse.getResponseWriter(
              HttpStatus.SC_OK,
              statusLine,
              DEFAULT_CONTENT_TYPE,
              Constants.CHARSET_NAME_UTF8,
              false);
      writer.write(configJson);
    } catch (IOException e) {
      logger.error(
          String.format("Exception serving %s with error %s", request.getRequestPath(), e));
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, e.getMessage(), e);
    }
  }
}
