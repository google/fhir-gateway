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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.HttpFhirClient;

/**
 * The factory for creating {@link AccessChecker} instances. A single instance of this might be used
 * for multiple queries; this is expected to be thread-safe.
 */
public interface AccessCheckerFactory {

  /**
   * Creates an AccessChecker for a given FHIR store and JWT. Note the scope of this is for a single
   * access token, i.e., one instance is created for each request.
   *
   * @param jwt the access token in the JWT format; after being validated and decoded.
   * @param httpFhirClient the client to use for accessing the FHIR store.
   * @param fhirContext the FhirContext object that can be used for creating other HAPI FHIR
   *     objects. This is an expensive object and should not be recreated for each access check.
   * @param patientFinder the utility class for finding patient IDs in query parameters/resources.
   * @return an AccessChecker; should never be {@code null}.
   * @throws AuthenticationException if an AccessChecker cannot be created for the given token; this
   *     is where AccessChecker specific errors can be communicated to the user.
   */
  AccessChecker create(
      DecodedJWT jwt,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext,
      PatientFinder patientFinder)
      throws AuthenticationException;
}
