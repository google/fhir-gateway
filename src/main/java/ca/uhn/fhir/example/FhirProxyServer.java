package ca.uhn.fhir.example;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/*")
public class FhirProxyServer extends RestfulServer {
   private static final Logger logger = LoggerFactory.getLogger(FhirProxyServer.class);

   private static final String PROXY_TO_ENV = "PROXY_TO";
   private static final String TOKEN_ISSUER_ENV = "TOKEN_ISSUER";

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

      // Create a context for the appropriate version
		setFhirContext(FhirContext.forR4());
		
      try {
         registerInterceptor(new BearerAuthorizationInterceptor(gcpFhirStore, tokenIssuer));
      } catch (IOException e) {
         ExceptionUtil.throwRuntimeExceptionAndLog(logger, "IOException while initializing", e);
      }
	}
}
