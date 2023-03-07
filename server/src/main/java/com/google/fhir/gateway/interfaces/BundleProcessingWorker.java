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
package com.google.fhir.gateway.interfaces;

import org.hl7.fhir.r4.model.Bundle;

/**
 * Interface to provide abstraction to process a Bundle entry by entry. This interface is consumed
 * by the {@link com.google.fhir.gateway.BundleProcessorUtils} to process the entire Bundle
 */
public interface BundleProcessingWorker {

  /**
   * Worker logic to process each entry in a Bundle
   *
   * @param bundleEntryComponent: an entry in a Bundle
   */
  void processBundleEntryComponent(Bundle.BundleEntryComponent bundleEntryComponent);

  /**
   * While processing each bundle entry in a Bundle, whether to process the next bundle entry. This
   * is to support early exit and avoid unnecessary processing of other entries in the Bundle
   *
   * @return boolean : if true should process the next entry in the Bundle, otherwise not
   */
  boolean processNextBundleEntry();
}
