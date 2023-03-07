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

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.gson.JsonArray;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;

public interface PatientFinder {
  /**
   * Finds the patient ID from the query if it is a direct Patient fetch (i.e., /Patient/PID) or the
   * patient can be inferred from query parameters.
   *
   * @param urlDetailsFinder any instance of the interface {@link UrlDetailsFinder}
   * @return the id of the patient that this query belongs to or null if it cannot be inferred.
   * @throws InvalidRequestException for various reasons when unexpected parameters or content are
   *     encountered. Callers are expected to deny access when this happens.
   */
  String findPatientFromUrl(UrlDetailsFinder urlDetailsFinder);

  /**
   * Finds all patients in the content of a request.
   *
   * @param resource that is expected to be of {@link IBaseResource} type.
   * @param resourceName name of the resource that is expected in the resource body .
   * @return Set<String> the set of patient IDs found in the references of the resource
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  Set<String> findPatientsInResource(IBaseResource resource, String resourceName);

  /**
   * Finds all patients in the body of a patch request
   *
   * @param jsonArray array of JSONs which contain the body of the resource that needs to be patched
   * @param resourceName the FHIR resource being patched
   * @return the set of patient ids in the patch
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  Set<String> findPatientsInPatchArray(JsonArray jsonArray, String resourceName);
}
