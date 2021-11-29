package com.google.fhir.proxy;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.IRestfulResponse;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Interceptor
public class BearerAuthorizationInterceptor {

  private static final Logger logger = LoggerFactory
      .getLogger(BearerAuthorizationInterceptor.class);

  private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";
  private static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();
  private static final String BEARER_PREFIX = "Bearer ";

  // TODO: Make this configurable or based on the given JWT; we should at least support some other
  // RSA* and ES* algorithms (requires ECDSA512 JWT algorithm).
  private static final String SIGN_ALGORITHM = "RS256";

  private final String tokenIssuer;
  private final Verification jwtVerifierConfig;
  private final HttpUtil httpUtil;
  private final RestfulServer server;
  private final HttpFhirClient fhirClient;
  private final AccessCheckerFactory accessFactory;

  BearerAuthorizationInterceptor(
      HttpFhirClient fhirClient,
      String tokenIssuer,
      RestfulServer server,
      HttpUtil httpUtil,
      AccessCheckerFactory accessFactory)
      throws IOException {
    Preconditions.checkNotNull(fhirClient);
    Preconditions.checkNotNull(server);
    this.server = server;
    this.fhirClient = fhirClient;
    this.httpUtil = httpUtil;
    this.tokenIssuer = tokenIssuer;
    this.accessFactory = accessFactory;
    RSAPublicKey issuerPublicKey = fetchAndDecodePublicKey();
    jwtVerifierConfig = JWT.require(Algorithm.RSA256(issuerPublicKey, null));
    logger.info("Created proxy to the FHIR store " + this.fhirClient.getBaseUrl());
  }

  private RSAPublicKey fetchAndDecodePublicKey() throws IOException {
    //Preconditions.checkState(SIGN_ALGORITHM.equals("ES512"));
    Preconditions.checkState(SIGN_ALGORITHM.equals("RS256"));
    //final String keyAlgorithm = "EC";
    final String keyAlgorithm = "RSA";
    try {
      // TODO: Make sure this works for any issuer not just Keycloak; instead of this we should
      // read the metadata and choose the right endpoint for the keys.
      HttpResponse response = httpUtil.getResourceOrFail(new URI(tokenIssuer));
      JsonObject jsonObject = JsonParser
          .parseString(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
          .getAsJsonObject();
      String keyStr = jsonObject.get("public_key").getAsString();
      if (keyStr == null) {
        ExceptionUtil
            .throwRuntimeExceptionAndLog(logger, "Cannot find 'public_key' in issuer metadata.");
      }
      KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);
      EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(keyStr));
      return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    } catch (URISyntaxException e) {
      ExceptionUtil
          .throwRuntimeExceptionAndLog(logger, "Error in token issuer URI " + tokenIssuer, e);
    } catch (NoSuchAlgorithmException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "Invalid algorithm " + keyAlgorithm, e);
    } catch (InvalidKeySpecException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, e);
    }
    // We should never get here, this is to keep the IDE happy!
    return null;
  }

  private JWTVerifier buildJwtVerifier(String issuer) {

    if (issuer.equals(tokenIssuer)) {
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
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          "Authorization header is not a valid Bearer token!", AuthenticationException.class);
    }
    String bearerToken = authHeader.substring(BEARER_PREFIX.length());
    DecodedJWT jwt = JWT.decode(bearerToken);
    String issuer = jwt.getIssuer();
    String algorithm = jwt.getAlgorithm();
    JWTVerifier jwtVerifier = buildJwtVerifier(issuer);
    logger.info(String
        .format("JWT issuer is %s, audience is %s, and algorithm is %s", issuer,
            jwt.getAudience(), algorithm));

    if (!algorithm.equals(SIGN_ALGORITHM)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, String.format(
          "Only %s signing algorithm is supported, got %s", SIGN_ALGORITHM, algorithm),
          AuthenticationException.class);
    }
    DecodedJWT verifiedJwt = null;
    try {
      verifiedJwt = jwtVerifier.verify(jwt);
    } catch (JWTVerificationException e) {
      // Throwing an AuthenticationException instead since it is handled by HAPI and a 401
      // status code is returned in the response.
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          String.format("JWT verification failed with error: %s", e.getMessage()), e,
          AuthenticationException.class);
    }
    return verifiedJwt;
  }

  private void checkAuthorization(DecodedJWT jwt, RequestDetails requestDetails) {
    AccessChecker accessChecker = accessFactory.create(jwt, fhirClient);
    if (!accessChecker.canAccess(requestDetails)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "User is not authorized to %s %s",
              requestDetails.getRequestType(), requestDetails.getCompleteUrl()),
          AuthenticationException.class);
    }
  }

  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
  public boolean authorizeRequest(RequestDetails requestDetails) {
    Preconditions.checkArgument(requestDetails instanceof ServletRequestDetails);
    ServletRequestDetails servletDetails = (ServletRequestDetails) requestDetails;
    logger.info("Started authorization check for URL " + requestDetails.getCompleteUrl());
    // Check the Bearer token to be a valid JWT with required claims.
    String authHeader = requestDetails.getHeader("Authorization");
    if (authHeader == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "No Authorization header provided!", AuthenticationException.class);
    }
    DecodedJWT decodedJwt = decodeAndVerifyBearerToken(authHeader);
    checkAuthorization(decodedJwt, requestDetails);
    String requestPath = requestDetails.getRequestPath();
    logger.debug("Authorized request path " + requestPath);
    try {
      HttpResponse response = fhirClient.handleRequest(servletDetails);
      HttpUtil.validateResponseEntityOrFail(response, requestPath);
      HttpEntity entity = response.getEntity();
      logger.debug(String.format("The response for %s is %s ", requestPath, response));
      logger.info("FHIR store response length: " + entity.getContentLength());
      IRestfulResponse proxyResponse = requestDetails.getResponse();
      for (Header header : fhirClient.responseHeadersToKeep(response)) {
        proxyResponse.addHeader(header.getName(), header.getValue());
      }
      // This should be called after adding headers.
      // TODO handle non-text responses, e.g., gzip.
      Writer writer = proxyResponse.getResponseWriter(response.getStatusLine().getStatusCode(),
          response.getStatusLine().toString(), DEFAULT_CONTENT_TYPE, DEFAULT_CHARSET, false);
      replaceAndCopyResponse(entity, writer, server.getServerBaseForRequest(servletDetails));
    } catch (IOException e) {
      logger.error(String
          .format("Exception for resource %s method %s with error: %s", requestPath,
              servletDetails.getServletRequest().getMethod(), e));
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, e);
    }

    // The request processing stops here, hence returning false.
    return false;
  }

  /**
   * Reads the content from the FHIR store response `entity`, replaces any FHIR store URLs by
   * the corresponding proxy URLs, and write the modified response to the proxy response `writer`.
   *
   * @param entity the entity part of the FHIR store response
   * @param writer the writer for proxy response
   * @param proxyBase the base URL of the proxy
   */
  private void replaceAndCopyResponse(HttpEntity entity, Writer writer, String proxyBase)
      throws IOException {
    // To make this more efficient, this only does a string search/replace; we may need to add
    // proper URL parsing if we need to address edge cases in URL no-op changes. This string
    // matching can be done more efficiently if needed, but we should avoid loading the full
    // stream in memory.
    ContentType contentType = ContentType.getOrDefault(entity);
    String charset = DEFAULT_CHARSET;
    if (contentType.getCharset() != null) {
      charset = contentType.getCharset().name();
    }
    String fhirStoreUrl = fhirClient.getBaseUrl();
    InputStreamReader reader = new InputStreamReader(entity.getContent(), charset);
    BufferedReader bufReader = new BufferedReader(reader);
    int numMatched = 0;
    int n;
    while ((n = bufReader.read()) >= 0) {
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

}
