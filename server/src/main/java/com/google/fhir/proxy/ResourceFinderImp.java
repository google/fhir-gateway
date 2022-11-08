/*
 * Copyright 2021-2022 Google LLC
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
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import com.google.fhir.proxy.interfaces.ResourceFinder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceFinderImp implements ResourceFinder {
  private static final Logger logger = LoggerFactory.getLogger(ResourceFinderImp.class);
  private static ResourceFinderImp instance = null;
  private final FhirContext fhirContext;

  // This is supposed to be instantiated with getInstance method only.
  private ResourceFinderImp(FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  private IBaseResource createResourceFromRequest(RequestDetailsReader request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    return jsonParser.parseResource(requestContent);
  }

  @Override
  public Set<String> findResourcesInResource(RequestDetailsReader request) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!resource.fhirType().equals(request.getResourceName())) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "The provided resource %s is different from what is on the path: %s ",
              resource.fhirType(), request.getResourceName()),
          InvalidRequestException.class);
    }

    Set<String> resourceIds = new HashSet<>();
    resourceIds.add(resource.getIdElement().getIdPart());
    return resourceIds;
  }

  @Override
  public List<BundleResources> findResourcesInBundle(RequestDetailsReader request) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!(resource instanceof Bundle)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "The provided resource is not a Bundle!", InvalidRequestException.class);
    }
    Bundle bundle = (Bundle) resource;

    if (bundle.getType() != Bundle.BundleType.TRANSACTION) {
      // Currently, support only for transaction bundles
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Bundle type needs to be transaction!", InvalidRequestException.class);
    }

    List<BundleResources> requestTypeEnumList = new ArrayList<>();
    if (!bundle.hasEntry()) {
      return requestTypeEnumList;
    }

    for (Bundle.BundleEntryComponent entryComponent : bundle.getEntry()) {
      Bundle.HTTPVerb httpMethod = entryComponent.getRequest().getMethod();
      if (httpMethod != Bundle.HTTPVerb.GET && !entryComponent.hasResource()) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "Bundle entry requires a resource field!", InvalidRequestException.class);
      }

      requestTypeEnumList.add(
          new BundleResources(
              RequestTypeEnum.valueOf(httpMethod.name()), entryComponent.getResource()));
    }

    return requestTypeEnumList;
  }

  // A singleton instance of this class should be used, hence the constructor is private.
  public static synchronized ResourceFinderImp getInstance(FhirContext fhirContext) {
    if (instance != null) {
      return instance;
    }

    instance = new ResourceFinderImp(fhirContext);
    return instance;
  }
}
