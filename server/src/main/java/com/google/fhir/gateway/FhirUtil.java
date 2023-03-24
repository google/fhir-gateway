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
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.common.base.Preconditions;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FhirUtil {

  private static final Logger logger = LoggerFactory.getLogger(FhirUtil.class);

  // This is based on https://www.hl7.org/fhir/datatypes.html#id
  private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

  public static boolean isSameResourceType(@Nullable String resourceType, ResourceType type) {
    return type.name().equals(resourceType);
  }

  public static String getIdOrNull(RequestDetailsReader requestDetails) {
    if (requestDetails.getId() == null) {
      return null;
    }
    return requestDetails.getId().getIdPart();
  }

  public static Bundle parseResponseToBundle(FhirContext fhirContext, HttpResponse httpResponse)
      throws IOException {
    Preconditions.checkState(HttpUtil.isResponseEntityValid(httpResponse));
    IParser jsonParser = fhirContext.newJsonParser();
    IBaseResource resource = jsonParser.parseResource(httpResponse.getEntity().getContent());
    Preconditions.checkArgument(
        FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Bundle));
    return (Bundle) resource;
  }

  public static IBaseResource createResourceFromRequest(
      FhirContext fhirContext, RequestDetailsReader request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    return jsonParser.parseResource(requestContent);
  }

  public static Bundle parseRequestToBundle(FhirContext fhirContext, RequestDetailsReader request) {
    IBaseResource resource = createResourceFromRequest(fhirContext, request);
    if (!(resource instanceof Bundle)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "The provided resource is not a Bundle!", InvalidRequestException.class);
    }
    return (Bundle) resource;
  }

  public static boolean isValidId(String id) {
    return ID_PATTERN.matcher(id).matches();
  }

  public static boolean isValidFhirResourceType(String resourceType) {
    try {
      ResourceType.fromCode(resourceType);
      return true;
    } catch (FHIRException fe) {
      return false;
    }
  }

  public static String checkIdOrFail(String idPart) {
    if (!isValidId(idPart)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, String.format("ID %s is invalid!", idPart), InvalidRequestException.class);
    }
    return idPart; // This is for convenience.
  }
}
