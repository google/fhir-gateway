/*
 * Copyright 2021-2024 Google LLC
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

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.fhir.gateway.BundlePatients;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;

public interface PatientFinder {
  /**
   * Finds the patient ID from the query if it is a direct Patient fetch (i.e., /Patient/PID) or the
   * patient can be inferred from query parameters.
   *
   * @param requestDetails the request
   * @return the ids of the patients that this query belongs to or an empty set if it cannot be
   *     inferred (never null).
   * @throws InvalidRequestException for various reasons when unexpected parameters or content are
   *     encountered. Callers are expected to deny access when this happens.
   */
  // TODO add @NotNull once we decide on null-check tooling.
  Set<String> findPatientsFromParams(RequestDetailsReader requestDetails);

  /**
   * Find all patients referenced or updated in a Bundle.
   *
   * @param bundle bundle request to find patient references in.
   * @return the {@link BundlePatients} that wraps all found patients.
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  BundlePatients findPatientsInBundle(Bundle bundle);

  /**
   * Finds all patients in the content of a request.
   *
   * @param request that is expected to have a Bundle content.
   * @return the {@link BundlePatients} that wraps all found patients.
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  Set<String> findPatientsInResource(RequestDetailsReader request);

  /**
   * Finds all patients in the body of a patch request
   *
   * @param request that is expected to have a body with a patch
   * @param resourceName the FHIR resource being patched
   * @return the set of patient ids in the patch
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  Set<String> findPatientsInPatch(RequestDetailsReader request, String resourceName);

  /**
   * Finds all patient ids in the content of a resource.
   *
   * @param resource belonging to {@link org.hl7.fhir.r4.model.codesystems.CompartmentType.PATIENT}
   *     that is expected to have patient ids.
   * @return the ids of the patient compartment owners
   */
  Set<String> findPatientIds(DomainResource resource);
}
