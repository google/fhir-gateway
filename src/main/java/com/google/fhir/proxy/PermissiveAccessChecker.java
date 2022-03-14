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
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.proxy.interfaces.AccessChecker;
import com.google.fhir.proxy.interfaces.AccessCheckerFactory;
import com.google.fhir.proxy.interfaces.AccessDecision;
import com.google.fhir.proxy.interfaces.PatientFinder;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;

/** This is the default no-op access-checker which lets all requests to go through. */
public class PermissiveAccessChecker implements AccessChecker {
  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    return new NoOpAccessDecision(true);
  }

  public static class Factory implements AccessCheckerFactory {
    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      return new PermissiveAccessChecker();
    }
  }
}
