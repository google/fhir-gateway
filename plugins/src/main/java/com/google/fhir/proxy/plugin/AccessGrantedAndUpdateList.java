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
package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.escape.Escaper;
import com.google.common.io.CharStreams;
import com.google.common.net.UrlEscapers;
import com.google.fhir.proxy.FhirUtil;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.HttpUtil;
import com.google.fhir.proxy.interfaces.AccessDecision;
import java.io.IOException;
import java.util.Set;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AccessGrantedAndUpdateList implements AccessDecision {

  private static final Logger logger = LoggerFactory.getLogger(AccessDecision.class);

  private final FhirContext fhirContext;
  private final HttpFhirClient httpFhirClient;
  private final String patientListId;
  private final Set<String> existPutPatients;
  private final ResourceType resourceTypeExpected;
  private final Escaper PARAM_ESCAPER = UrlEscapers.urlFormParameterEscaper();

  private AccessGrantedAndUpdateList(
      String patientListId,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext,
      Set<String> existPutPatient,
      ResourceType resourceTypeExpected) {
    this.patientListId = patientListId;
    this.fhirContext = fhirContext;
    this.httpFhirClient = httpFhirClient;
    this.existPutPatients = existPutPatient;
    this.resourceTypeExpected = resourceTypeExpected;
  }

  @Override
  public boolean canAccess() {
    return true;
  }

  @Override
  public String postProcess(HttpResponse response) throws IOException {
    Preconditions.checkState(HttpUtil.isResponseValid(response));
    String content = CharStreams.toString(HttpUtil.readerFromEntity(response.getEntity()));
    IParser parser = fhirContext.newJsonParser();
    IBaseResource resource = parser.parseResource(content);

    if (!FhirUtil.isSameResourceType(resource.fhirType(), resourceTypeExpected)) {
      String errorMessage =
          String.format(
              "Expected to get a %s resource; got: %s ", resourceTypeExpected, resource.fhirType());
      logger.error(errorMessage);
      return content;
    }

    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
      IIdType id = resource.getIdElement();
      String patientFromResource = id.getIdPart();
      addPatientToList(patientFromResource);
    }

    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Bundle)) {
      // TODO Response potentially too large to be loaded into memory; see:
      //   https://github.com/google/fhir-access-proxy/issues/64
      Bundle bundle = (Bundle) parser.parseResource(content);

      Set<String> patientIdsInResponse = Sets.newHashSet();
      for (BundleEntryComponent entryComponent : bundle.getEntry()) {
        IIdType resourceId =
            new Reference(entryComponent.getResponse().getLocation()).getReferenceElement();

        if (FhirUtil.isSameResourceType(resourceId.getResourceType(), ResourceType.Patient)) {
          patientIdsInResponse.add(resourceId.getIdPart());
        }
      }
      patientIdsInResponse.removeAll(existPutPatients);
      for (String patientId : patientIdsInResponse) {
        addPatientToList(patientId);
      }
    }
    return content;
  }

  private void addPatientToList(String newPatient) throws IOException {
    Preconditions.checkNotNull(newPatient);
    // TODO create this with HAPI client instead of handcrafting; see:
    //   https://github.com/google/fhir-access-proxy/issues/65
    String jsonPatch =
        String.format(
            "[{"
                + "  \"op\": \"add\","
                + "  \"path\": \"/entry/-\","
                + "  \"value\":"
                + "  {"
                + "    \"item\": {"
                + "      \"reference\": \"Patient/%s\""
                + "    }"
                + "  }"
                + "}]",
            newPatient);
    logger.info("Updating access list {} with patch {}", patientListId, jsonPatch);
    // TODO decide how to handle failures in access list updates; see:
    //   https://github.com/google/fhir-access-proxy/issues/66
    httpFhirClient.patchResource(
        String.format("List/%s", PARAM_ESCAPER.escape(patientListId)), jsonPatch);
  }

  public static AccessGrantedAndUpdateList forPatientResource(
      String patientListId, HttpFhirClient httpFhirClient, FhirContext fhirContext) {
    return new AccessGrantedAndUpdateList(
        patientListId, httpFhirClient, fhirContext, Sets.newHashSet(), ResourceType.Patient);
  }

  public static AccessGrantedAndUpdateList forBundle(
      String patientListId,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext,
      Set<String> existPutPatients) {
    return new AccessGrantedAndUpdateList(
        patientListId, httpFhirClient, fhirContext, existPutPatients, ResourceType.Bundle);
  }
}
