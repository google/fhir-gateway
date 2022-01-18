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

import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.auth0.jwt.interfaces.DecodedJWT;

/** This is the default no-op access-checker which lets all requests to go through. */
public class PermissiveAccessChecker implements AccessChecker {
  @Override
  public AccessDecision checkAccess(RequestDetails requestDetails) {
    return new NoOpAccessDecision(true);
  }

  public static class Factory implements AccessCheckerFactory {
    @Override
    public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      return new PermissiveAccessChecker();
    }
  }
}
