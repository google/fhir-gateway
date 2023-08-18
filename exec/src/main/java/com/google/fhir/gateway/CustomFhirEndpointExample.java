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
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.IParser;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.ListResource.ListEntryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an example servlet that requires a valid JWT to be present as the Bearer Authorization
 * header. Although it is not a standard FHIR query, but it uses the FHIR server to construct the
 * response. In this example, it inspects the JWT and depending on its claims, constructs the list
 * of Patient IDs that the user has access to.
 *
 * <p>The two types of tokens resemble {@link com.google.fhir.gateway.plugin.ListAccessChecker} and
 * {@link com.google.fhir.gateway.plugin.PatientAccessChecker} expected tokens. But those are just
 * picked as examples and this custom endpoint is independent of any {@link
 * com.google.fhir.gateway.interfaces.AccessChecker}.
 */
@WebServlet("/myPatients")
public class CustomFhirEndpointExample extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(CustomFhirEndpointExample.class);
  private final TokenVerifier tokenVerifier;

  private final HttpFhirClient fhirClient;

  public CustomFhirEndpointExample() throws IOException {
    this.tokenVerifier = TokenVerifier.createFromEnvVars();
    this.fhirClient = FhirClientFactory.createFhirClientFromEnvVars();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Check the Bearer token to be a valid JWT with required claims.
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      throw new ServletException("No Authorization header provided!");
    }
    List<String> patientIds = new ArrayList<>();
    // Note for a more meaningful HTTP status code, we can catch AuthenticationException in:
    DecodedJWT jwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);
    Claim claim = jwt.getClaim("patient_list");
    if (claim.asString() != null) {
      logger.info("Found a 'patient_list' claim: {}", claim);
      String listUri = "List/" + claim.asString();
      HttpResponse fhirResponse = fhirClient.getResource(listUri);
      HttpUtil.validateResponseOrFail(fhirResponse, listUri);
      if (fhirResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        logger.error("Error while fetching {}", listUri);
        response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        return;
      }
      FhirContext fhirContext = FhirContext.forCached(FhirVersionEnum.R4);
      IParser jsonParser = fhirContext.newJsonParser();
      IBaseResource resource = jsonParser.parseResource(fhirResponse.getEntity().getContent());
      ListResource listResource = (ListResource) resource;
      for (ListEntryComponent entry : listResource.getEntry()) {
        patientIds.add(entry.getItem().getReference());
      }
    } else {
      claim = jwt.getClaim("patient_id");
      if (claim.asString() != null) {
        logger.info("Found a 'patient_id' claim: {}", claim);
        patientIds.add(claim.asString());
      }
    }
    if (claim.asString() == null) {
      String error = "Found no patient claim in the token!";
      logger.error(error);
      response.getOutputStream().print(error);
      response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    response.getOutputStream().print("Your patient are: " + String.join(" ", patientIds));
    response.setStatus(HttpStatus.SC_OK);
  }
}
