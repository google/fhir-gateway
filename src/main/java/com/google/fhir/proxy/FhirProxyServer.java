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
import ca.uhn.fhir.rest.server.RestfulServer;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    logger.info("Adding BearerAuthorizationInterceptor ");
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

    try {
      AccessCheckerFactory checkerFactory = new PermissiveAccessChecker.Factory();
      String accessCheckerType = System.getenv(ACCESS_CHECKER_ENV);
      if (LIST_ACCESS_CHECKER.equals(accessCheckerType)) {
        logger.info(String.format("Patient access-checker is '%s'", accessCheckerType));
        checkerFactory = new ListAccessChecker.Factory(this);
      } else {
        logger.warn(
            String.format(
                "Environment variable %s is not recognized; disabling Patient access-checker!",
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
}
