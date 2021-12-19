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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;

public class FhirUtil {

  static boolean isSameResourceType(@Nullable String resourceType, ResourceType type) {
    return type.name().equals(resourceType);
  }

  static String getIdOrNull(RequestDetails requestDetails) {
    if (requestDetails.getId() == null) {
      return null;
    }
    return requestDetails.getId().getIdPart();
  }

  static Bundle parseResponseToBundle(FhirContext fhirContext, HttpResponse httpResponse)
      throws IOException {
    Preconditions.checkState(HttpUtil.isResponseEntityValid(httpResponse));
    IParser jsonParser = fhirContext.newJsonParser();
    IBaseResource resource = jsonParser.parseResource(httpResponse.getEntity().getContent());
    Preconditions.checkArgument(
        FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Bundle));
    return (Bundle) resource;
  }
}
