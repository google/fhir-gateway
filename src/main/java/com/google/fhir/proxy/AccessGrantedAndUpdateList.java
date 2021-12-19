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
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AccessGrantedAndUpdateList implements AccessDecision {

  private static final Logger logger = LoggerFactory.getLogger(AccessDecision.class);

  private final FhirContext fhirContext;
  private final HttpFhirClient httpFhirClient;
  private final String patientListId;
  private final String newPutPatient;

  AccessGrantedAndUpdateList(
      String patientListId,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext,
      @Nullable String newPutPatient) {
    this.patientListId = patientListId;
    this.fhirContext = fhirContext;
    this.httpFhirClient = httpFhirClient;
    this.newPutPatient = newPutPatient;
  }

  @Override
  public boolean canAccess() {
    return true;
  }

  @Override
  public String postProcess(HttpResponse response, RequestDetails requestDetails)
      throws IOException {
    Preconditions.checkState(HttpUtil.isResponseValid(response));
    String content = null;
    String newPatient = null;
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      if (requestDetails.getRequestType() == RequestTypeEnum.PUT) {
        newPatient = newPutPatient;
      } else if (requestDetails.getRequestType() == RequestTypeEnum.POST) {
        content = CharStreams.toString(HttpUtil.readerFromEntity(response.getEntity()));
        IParser parser = fhirContext.newJsonParser();
        IBaseResource resource = parser.parseResource(content);
        if (!FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
          // We are expecting a Patient resource; this is an error!
          logger.error("Expected to get a Patient resource; got: " + resource.fhirType());
          return content;
        }
        IIdType id = resource.getIdElement();
        if (id == null) {
          logger.error("The updated Patient has no 'id' element!");
          return content;
        }
        newPatient = id.getIdPart();
      }
      if (newPatient != null) {
        addPatientToList(newPatient);
      }
    }
    return content;
  }

  private void addPatientToList(String newPatient) throws IOException {
    Preconditions.checkNotNull(newPatient);
    // TODO create this with HAPI client instead of handcrafting (b/211231483)!
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
    // TODO decide how to handle failures in access list updates (b/211243404).
    httpFhirClient.patchResource(String.format("List/%s", patientListId), jsonPatch);
  }
}
