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
package com.google.fhir.proxy.interfaces;

/**
 * The main interface for deciding whether to grant access to a request or not. Implementations of
 * this do not have to be thread-safe as it is guaranteed by the server code not to call {@code
 * checkAccess} concurrently.
 */
public interface AccessChecker {

  /**
   * Checks whether the current user has access to requested resources.
   *
   * @param requestDetails details about the resource and operation requested
   * @return the outcome of access checking
   */
  AccessDecision checkAccess(RequestDetailsReader requestDetails);
}
