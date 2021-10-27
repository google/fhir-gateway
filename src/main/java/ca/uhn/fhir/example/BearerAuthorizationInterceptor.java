package ca.uhn.fhir.example;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.IRestfulResponse;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
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
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Interceptor
public class BearerAuthorizationInterceptor {

  private static final Logger logger = LoggerFactory
      .getLogger(BearerAuthorizationInterceptor.class);

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String FHIR_USER = "fhirUser";

  // TODO: Make this configurable or based on the given JWT; we should at least support some other
  // RSA* and ES* algorithms (requires ECDSA512 JWT algorithm).
  private static final String SIGN_ALGORITHM = "RS256";

  private final String tokenIssuer;
  private final JWTVerifier jwtVerifier;
  // TODO: Add dependency-injection.
  private final HttpFhirClient httpFhirClient;
  private final HttpUtil httpUtil;

  BearerAuthorizationInterceptor(String gcpFhirStore, String tokenIssuer) throws IOException {
    Preconditions.checkNotNull(gcpFhirStore);
    httpFhirClient = new GcpFhirClient(gcpFhirStore);
    httpUtil = new HttpUtil();
    this.tokenIssuer = tokenIssuer;
    RSAPublicKey issuerPublicKey = fetchAndDecodePublicKey();
    jwtVerifier = JWT.require(Algorithm.RSA256(issuerPublicKey, null)).withIssuer(tokenIssuer)
        .build();
  }


  private RSAPublicKey fetchAndDecodePublicKey() throws IOException {
    //Preconditions.checkState(SIGN_ALGORITHM.equals("ES512"));
    Preconditions.checkState(SIGN_ALGORITHM.equals("RS256"));
    //final String keyAlgorithm = "EC";
    final String keyAlgorithm = "RSA";
    try {
      // TODO: Make sure this works for any issuer not just Keycloak; instead of this we should
      // read the metadata and choose the right endpoint for the keys.
      HttpResponse response = httpUtil.getResource(new URI(tokenIssuer));
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

  private void verifyBearerToken(String authHeader) {
    if (!authHeader.startsWith(BEARER_PREFIX)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          "Authorization header is not a valid Bearer token!", AuthenticationException.class);
    }
    String bearerToken = authHeader.substring(BEARER_PREFIX.length());
    DecodedJWT jwt = JWT.decode(bearerToken);
    String issuer = jwt.getIssuer();
    String algorithm = jwt.getAlgorithm();
    logger.info(String
        .format("JWT issuer is %s, audience is %s, and algorithm is %s", issuer,
            jwt.getAudience(), algorithm));
    // This and some other checks can be moved to jwtVerifier too.
    if (issuer == null || !issuer.equals(tokenIssuer)) {
      ExceptionUtil
          .throwRuntimeExceptionAndLog(logger, "The token issuer (iss) is not " + tokenIssuer,
              AuthenticationException.class);
    }
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
    Claim groupClaim = verifiedJwt.getClaim("group");
    if (groupClaim == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "Bearer JWT has no `group` claim!",
          AuthenticationException.class);
    }
    List<String> groups = groupClaim.asList(String.class);
    if (groups == null || !groups.contains(FHIR_USER)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          "Bearer JWT does not have the expected group " + FHIR_USER,
          AuthenticationException.class);
    }
  }

  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLER_SELECTED)
  public boolean authorizeRequests(RequestDetails requestDetails) {
    Preconditions.checkArgument(requestDetails instanceof ServletRequestDetails);
    ServletRequestDetails servletDetails = (ServletRequestDetails) requestDetails;
    logger.info("Started authorization check for URI " + servletDetails.getServletRequest()
        .getRequestURI());
    // Check the Bearer token to be a valid JWT with required claims.
    String authHeader = requestDetails.getHeader("Authorization");
    if (authHeader == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "No Authorization header provided!",
          AuthenticationException.class);
    }
    // TODO add patient compartment check
    verifyBearerToken(authHeader);
    String requestPath = requestDetails.getRequestPath();
    logger.debug("Authorized request path " + requestPath);
    try {
      HttpResponse response = httpFhirClient.handleRequest(servletDetails);
      HttpEntity entity = response.getEntity();
      if (entity == null) {
        ExceptionUtil
            .throwRuntimeExceptionAndLog(logger, "Nothing received from the FHIR server!");
      }
      logger.debug(String.format("The response for %s is %s ", requestPath, response));
      IRestfulResponse proxyResponse = requestDetails.getResponse();
      for (Header header : httpFhirClient.responseHeadersToKeep(response)) {
        proxyResponse.addHeader(header.getName(), header.getValue());
      }
      // This should be called after adding headers.
      Writer writer = proxyResponse.getResponseWriter(response.getStatusLine().getStatusCode(),
          response.getStatusLine().toString(), entity.getContentType().getValue(),
          StandardCharsets.UTF_8.name(), false);
      // If request and response charsets are the same we can binary copy.
      // TODO: Test performance difference and consider charset similarity enforcing.
      IOUtils.copy(entity.getContent(), writer, StandardCharsets.UTF_8.name());
    } catch (IOException e) {
      logger.error(String
          .format("Exception for resource %s method %s with error: %s", requestPath,
              servletDetails.getServletRequest().getMethod(), e));
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, e);
    }

    // The request processing stops here, hence returning false.
    return false;
  }
}
