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
import ca.uhn.fhir.parser.IParser;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.codesystems.RestfulSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapabilityPostProcessor implements AccessDecision {
  private static final Logger logger = LoggerFactory.getLogger(AccessDecision.class);
  private static final String SECURITY_DESCRIPTION =
      "To access FHIR resources behind this proxy, each request needs a Bearer Authorization "
          + "header containing a JWT access token. This token must have been issued by the "
          + "authorization server defined by the configured TOKEN_ISSUER.";
  private static CapabilityPostProcessor instance = null;

  private final FhirContext fhirContext;

  private CapabilityPostProcessor(FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  static synchronized CapabilityPostProcessor getInstance(FhirContext fhirContext) {
    if (instance == null) {
      instance = new CapabilityPostProcessor(fhirContext);
    }
    return instance;
  }

  @Override
  public RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {
    return null;
  }

  @Override
  public boolean canAccess() {
    return true;
  }

  @Override
  public String postProcess(RequestDetailsReader requestDetails, HttpResponse response)
      throws IOException {
    Preconditions.checkState(HttpUtil.isResponseValid(response));
    String content = CharStreams.toString(HttpUtil.readerFromEntity(response.getEntity()));
    IParser parser = fhirContext.newJsonParser();
    IBaseResource resource = parser.parseResource(content);

    if (!FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.CapabilityStatement)) {
      String errorMessage =
          String.format(
              "Expected to get a %s resource; got: %s ",
              ResourceType.CapabilityStatement, resource.fhirType());
      logger.error(errorMessage);
      return content;
    }
    CapabilityStatement capability = (CapabilityStatement) resource;
    if (!capability.hasRest()) {
      return content;
    }
    for (CapabilityStatementRestComponent rest : capability.getRest()) {
      addCors(rest.getSecurity());
    }
    return parser.encodeResourceToString(capability);
  }

  private void addCors(CapabilityStatementRestSecurityComponent security) {
    // See FhirProxyServer.registerCorsInterceptor for our default CORS support.
    security.setCors(true);
    security
        .addService()
        .addCoding()
        .setSystem(RestfulSecurityService.OAUTH.getSystem())
        .setCode(RestfulSecurityService.OAUTH.toCode());
    security.setDescription(SECURITY_DESCRIPTION);
  }
}
