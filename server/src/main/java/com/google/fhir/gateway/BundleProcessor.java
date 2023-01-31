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
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleProcessor {
  private static final Logger logger = LoggerFactory.getLogger(BundleProcessor.class);

  private final FhirContext fhirContext;

  public BundleProcessor(FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  public <R> R processBundleFromRequest(
      RequestDetailsReader request,
      Map<HTTPVerb, Consumer<BundleEntryComponent>> entryComponentProcessors,
      Supplier<R> postEntriesProcessor) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!(resource instanceof Bundle)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "The provided resource is not a Bundle!", InvalidRequestException.class);
    }
    Bundle bundle = (Bundle) resource;

    if (bundle.getType() != BundleType.TRANSACTION) {
      // Currently, support only for transaction bundles; see:
      //   https://github.com/google/fhir-access-proxy/issues/67
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Bundle type needs to be transaction!", InvalidRequestException.class);
    }

    for (BundleEntryComponent entryComponent : bundle.getEntry()) {
      HTTPVerb httpMethod = entryComponent.getRequest().getMethod();
      if (httpMethod != HTTPVerb.GET
          && httpMethod != HTTPVerb.DELETE
          && !entryComponent.hasResource()) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "Bundle entry requires a resource field!", InvalidRequestException.class);
      }
      getComponentProcessorOrThrowException(entryComponentProcessors, httpMethod)
          .accept(entryComponent);
    }
    return postEntriesProcessor.get();
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

  private Consumer<BundleEntryComponent> getComponentProcessorOrThrowException(
      Map<HTTPVerb, Consumer<BundleEntryComponent>> entryComponentProcessors, HTTPVerb httpMethod) {
    if (!entryComponentProcessors.containsKey(httpMethod)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format("HTTP request method %s is not supported!", httpMethod),
          InvalidRequestException.class);
    }
    return entryComponentProcessors.get(httpMethod);
  }
}
