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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import java.io.IOException;
import java.util.ArrayList;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;

@WebServlet("/*")
public class FhirProxyServer extends RestfulServer {

  private static final Logger logger = LoggerFactory.getLogger(FhirProxyServer.class);

  private static final String PROXY_TO_ENV = "PROXY_TO";
  private static final String TOKEN_ISSUER_ENV = "TOKEN_ISSUER";
  private static final String ACCESS_CHECKER_ENV = "ACCESS_CHECKER";
  private static final String LIST_ACCESS_CHECKER = "list";

  static boolean isDevMode() {
    String runMode = System.getenv("RUN_MODE");
    return "DEV".equals(runMode);
  }

  @Override
  protected void initialize() throws ServletException {
    String gcpFhirStore = System.getenv(PROXY_TO_ENV);
    if (gcpFhirStore == null) {
      throw new ServletException(
          String.format("The environment variable %s is not set!", PROXY_TO_ENV));
    }
    String tokenIssuer = System.getenv(TOKEN_ISSUER_ENV);
    if (tokenIssuer == null) {
      throw new ServletException(
          String.format("The environment variable %s is not set!", TOKEN_ISSUER_ENV));
    }

    // TODO make the FHIR version configurable.
    // Create a context for the appropriate version
    setFhirContext(FhirContext.forR4());

    // Note interceptor registration order is important.
    registerCorsInterceptor();

    try {
      logger.info("Adding BearerAuthorizationInterceptor ");
      AccessCheckerFactory checkerFactory = new PermissiveAccessChecker.Factory();
      String accessCheckerType = System.getenv(ACCESS_CHECKER_ENV);
      if (LIST_ACCESS_CHECKER.equals(accessCheckerType)) {
        logger.info(String.format("Patient access-checker is '%s'", accessCheckerType));
        checkerFactory = new ListAccessChecker.Factory(this);
      } else {
        if (!isDevMode()) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger,
              String.format("Environment variable %s is not recognized!", ACCESS_CHECKER_ENV));
        }
        logger.warn(
            String.format(
                "Env. variable %s is not recognized; disabling Patient access-checker (DEV mode)!",
                ACCESS_CHECKER_ENV));
      }
      registerInterceptor(
          new BearerAuthorizationInterceptor(
              new GcpFhirClient(gcpFhirStore, GcpFhirClient.createCredentials()),
              tokenIssuer,
              this,
              new HttpUtil(),
              checkerFactory));
    } catch (IOException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "IOException while initializing", e);
    }
  }

  /**
   * This is to add a CORS interceptor for adding "Cross-Origin Resource Sharing" headers:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS. We need this for example for Javascript
   * SMART apps that need to fetch FHIR resources. Enabling CORS on all paths should be fine because
   * we don't rely on cookies for access-tokens and access to any sensitive resource is protected by
   * a Bearer access-token.
   *
   * <p>We could have used servlet filters but this is the "cleaner" native HAPI solution:
   * https://hapifhir.io/hapi-fhir/docs/security/cors.html. The drawback of this is the extra Spring
   * dependency. It is probably okay as it increased the size of the war file from 35 MB to 38 MB
   * (and opens the door to other Spring goodies too).
   */
  private void registerCorsInterceptor() {
    // Create the CORS interceptor and register it. This mostly relies on the default config used in
    // `CorsInterceptor.createDefaultCorsConfig`; the main difference is the authorization header.
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedHeaders(new ArrayList<>(Constants.CORS_ALLOWED_HEADERS));
    config.setAllowedMethods(new ArrayList<>(Constants.CORS_ALLWED_METHODS));
    config.addAllowedHeader(Constants.HEADER_AUTHORIZATION);
    config.addAllowedOrigin("*");

    CorsInterceptor interceptor = new CorsInterceptor(config);
    registerInterceptor(interceptor);
  }
}
