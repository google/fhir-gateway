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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** Defines mutations that can be applied to the incoming request by an {@link AccessChecker}. */
@Builder
@Getter
public class RequestMutation {

  // Additional query parameters and list of values for a parameter that should be added to the
  // outgoing FHIR request.
  // New values overwrites the old one if there is a conflict for a request param (i.e. a returned
  // parameter in RequestMutation is already present in the original request).
  // Old parameter values should be explicitly retained while mutating values for that parameter.
  @Builder.Default Map<String, List<String>> queryParams = new HashMap<>();
}
