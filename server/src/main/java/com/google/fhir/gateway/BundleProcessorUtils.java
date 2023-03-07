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
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.fhir.gateway.interfaces.BundleProcessingWorker;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to process a Bundle entry by entry based on the logic provided by the {@link
 * BundleProcessingWorker}
 */
public class BundleProcessorUtils {
  private static final Logger logger = LoggerFactory.getLogger(BundleProcessorUtils.class);

  private static BundleProcessorUtils instance = null;
  private final FhirContext fhirContext;

  public BundleProcessorUtils(FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  public static synchronized BundleProcessorUtils getInstance(FhirContext fhirContext) {
    if (instance != null) {
      return instance;
    }
    instance = new BundleProcessorUtils(fhirContext);
    return instance;
  }

  public void processBundleFromRequest(
      RequestDetailsReader request, BundleProcessingWorker bundleProcessingWorker) {
    IBaseResource resource = FhirUtil.createResourceFromRequest(fhirContext, request);
    if (!(resource instanceof Bundle)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "The provided resource is not a Bundle!", InvalidRequestException.class);
    }
    assert resource instanceof Bundle;
    Bundle bundle = (Bundle) resource;

    if (bundle.getType() != BundleType.TRANSACTION) {
      // Currently, support only for transaction bundles; see:
      //   https://github.com/google/fhir-access-proxy/issues/67
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Bundle type needs to be transaction!", InvalidRequestException.class);
    }

    for (BundleEntryComponent entryComponent : bundle.getEntry()) {
      if (bundleProcessingWorker.processNextBundleEntry()) {
        bundleProcessingWorker.processBundleEntryComponent(entryComponent);
      } else {
        break;
      }
    }
  }
}
