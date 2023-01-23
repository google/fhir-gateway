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
package com.google.fhir.gateway.plugin;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.fhir.gateway.BundlePatients;
import com.google.fhir.gateway.BundlePatients.BundlePatientsBuilder;
import com.google.fhir.gateway.FhirUtil;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.HttpUtil;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Named;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_list` ID in the access token to fetch the "List" of patient
 * IDs that the given user has access to.
 */
public class ListAccessChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(ListAccessChecker.class);
  private final FhirContext fhirContext;
  private final HttpFhirClient httpFhirClient;
  private final String patientListId;
  private final PatientFinder patientFinder;
  private final Escaper PARAM_ESCAPER = UrlEscapers.urlFormParameterEscaper();

  private ListAccessChecker(
      HttpFhirClient httpFhirClient,
      String patientListId,
      FhirContext fhirContext,
      PatientFinder patientFinder) {
    this.fhirContext = fhirContext;
    this.httpFhirClient = httpFhirClient;
    this.patientListId = patientListId;
    this.patientFinder = patientFinder;
  }

  /**
   * Sends query to backend with user supplied parameters
   *
   * @param itemsParam resources to search for in the list. Must start with "item=". It is assumed
   *     that this is properly escaped to be added to the URL string.
   * @return the outcome of access checking. Returns false if no parameter is provided, or if the
   *     parameter does not start with "item=", or if the query does not return exactly one match.
   */
  private boolean listIncludesItems(String itemsParam) {
    Preconditions.checkArgument(itemsParam.startsWith("item="));
    if (itemsParam.equals("item=")) {
      return false;
    }
    // We cannot use `_summary` parameter because it is not implemented on GCP yet; so to prevent a
    // potentially huge list to be fetched each time, we add `_elements=id`.
    String searchQuery =
        String.format(
            "/List?_id=%s&_elements=id&%s", PARAM_ESCAPER.escape(this.patientListId), itemsParam);
    logger.debug("Search query for patient access authorization check is: {}", searchQuery);
    try {
      HttpResponse httpResponse = httpFhirClient.getResource(searchQuery);
      HttpUtil.validateResponseEntityOrFail(httpResponse, searchQuery);
      Bundle bundle = FhirUtil.parseResponseToBundle(fhirContext, httpResponse);
      // We expect exactly one result which is `patientListId`.
      return bundle.getTotal() == 1;
    } catch (IOException e) {
      logger.error("Exception while accessing " + searchQuery, e);
    }
    return false;
  }
  // Note this returns true iff at least one of the patient IDs is found in the associated list.
  // The rationale is that a user should have access to a resource iff they are authorized to access
  // at least one of the patients referenced in that resource. This is a subjective decision, so we
  // may want to revisit it in the future.
  // Note patientIds are expected NOT to include the `Patient/` prefix (pure IDs only).
  private boolean serverListIncludesAnyPatient(Set<String> patientIds) {
    if (patientIds == null) {
      return false;
    }
    // TODO consider using the HAPI FHIR client instead; see:
    //   https://github.com/google/fhir-access-proxy/issues/65.
    String patientParam =
        queryBuilder(patientIds, PARAM_ESCAPER.escape("Patient/"), PARAM_ESCAPER.escape(","));
    return listIncludesItems("item=" + patientParam);
  }

  // Returns true iff all the patient IDs are found in the associated list.
  // Note patientIds are expected to include the `Patient/` prefix.
  // TODO fix the above inconsistency with `serverListIncludesAnyPatient`.
  private boolean serverListIncludesAllPatients(Set<String> patientIds) {
    if (patientIds == null) {
      return false;
    }
    String patientParam = queryBuilder(patientIds, "item=", "&");
    return listIncludesItems(patientParam);
  }

  private boolean patientsExist(String patientId) throws IOException {
    // TODO consider using the HAPI FHIR client instead; see:
    //   https://github.com/google/fhir-access-proxy/issues/65
    String searchQuery =
        String.format("/Patient?_id=%s&_elements=id", PARAM_ESCAPER.escape(patientId));
    HttpResponse response = httpFhirClient.getResource(searchQuery);
    Bundle bundle = FhirUtil.parseResponseToBundle(fhirContext, response);
    if (bundle.getTotal() > 1) {
      logger.error(
          String.format(
              "%s patients with the same ID of one of ths %s returned from the FHIR store.",
              bundle.getTotal(), patientId));
    }
    return (bundle.getTotal() > 0);
  }

  /**
   * Inspects the given request to make sure that it is for a FHIR resource of a patient that the
   * current user has access too; i.e., the patient is in the patient-list associated to the user.
   *
   * @param requestDetails the original request sent to the proxy.
   * @return true iff patient is in the patient-list associated to the current user.
   */
  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    try {
      // For a Bundle requestDetails.getResourceName() returns null
      if (requestDetails.getRequestType() == RequestTypeEnum.POST
          && requestDetails.getResourceName() == null) {
        return processBundle(requestDetails);
      }
      // Process non-Bundle requests
      switch (requestDetails.getRequestType()) {
        case GET:
          return processGet(requestDetails);
        case POST:
          return processPost(requestDetails);
        case PUT:
          return processPut(requestDetails);
        case PATCH:
          return processPatch(requestDetails);
        case DELETE:
          return processDelete(requestDetails);
        default:
          return NoOpAccessDecision.accessDenied();
      }
    } catch (IOException e) {
      logger.error("Exception while checking patient existence; denying access! ", e);
      return NoOpAccessDecision.accessDenied();
    }
  }

  private AccessDecision processGet(RequestDetailsReader requestDetails) {
    // There should be a patient id in search params; the param name is based on the resource.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.List)) {
      if (patientListId.equals(FhirUtil.getIdOrNull(requestDetails))) {
        return NoOpAccessDecision.accessGranted();
      }
      return NoOpAccessDecision.accessDenied();
    }
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(serverListIncludesAnyPatient(Sets.newHashSet(patientId)));
  }

  private AccessDecision processPost(RequestDetailsReader requestDetails) {
    // We have decided to let clients add new patients while understanding its security risks.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return AccessGrantedAndUpdateList.forPatientResource(
          patientListId, httpFhirClient, fhirContext);
    }
    Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(serverListIncludesAnyPatient(patientIds));
  }

  private AccessDecision processPut(RequestDetailsReader requestDetails) throws IOException {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      AccessDecision accessDecision = checkPatientAccessInUpdate(requestDetails);
      if (accessDecision == null) {
        return AccessGrantedAndUpdateList.forPatientResource(
            patientListId, httpFhirClient, fhirContext);
      }
      return accessDecision;
    }
    return checkNonPatientAccessInUpdate(requestDetails, RequestTypeEnum.PUT);
  }

  private AccessDecision processPatch(RequestDetailsReader requestDetails) throws IOException {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      AccessDecision accessDecision = checkPatientAccessInUpdate(requestDetails);
      if (accessDecision == null) {
        logger.error("Creating a new Patient via PATCH is not allowed");
        return NoOpAccessDecision.accessDenied();
      }
      return accessDecision;
    }
    return checkNonPatientAccessInUpdate(requestDetails, RequestTypeEnum.PATCH);
  }

  private AccessDecision processDelete(RequestDetailsReader requestDetails) {
    // We don't support deletion of List resource used as an access list for a user.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.List)) {
      if (patientListId.equals(FhirUtil.getIdOrNull(requestDetails))) {
        return NoOpAccessDecision.accessGranted();
      }
      return NoOpAccessDecision.accessDenied();
    }

    // TODO(https://github.com/google/fhir-access-proxy/issues/63):Support direct resource deletion.

    // There should be a patient id in search params; the param name is based on the resource.
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(serverListIncludesAnyPatient(Sets.newHashSet(patientId)));
  }

  private AccessDecision checkNonPatientAccessInUpdate(
      RequestDetailsReader requestDetails, RequestTypeEnum updateMethod) {
    Preconditions.checkArgument(
        (updateMethod == RequestTypeEnum.PATCH) || (updateMethod == RequestTypeEnum.PUT),
        "Expected either PATCH or PUT!");

    // We do not allow direct resource PUT/PATCH, so Patient ID must be returned
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    Set<String> patientQueries = Sets.newHashSet();
    // Escaping is not needed here as the set elements will be escaped later.
    patientQueries.add(String.format("Patient/%s", patientId));

    Set<String> patientSet = Sets.newHashSet();
    if (updateMethod == RequestTypeEnum.PATCH) {
      patientSet =
          patientFinder.findPatientsInPatch(requestDetails, requestDetails.getResourceName());
    }
    if (updateMethod == RequestTypeEnum.PUT) {
      patientSet = patientFinder.findPatientsInResource(requestDetails);
      // One patient referenced in PUT needs to be accessible by client.
      if (patientSet.isEmpty()) {
        logger.error("No Patient ID referenced in PUT body; denying access!");
        return NoOpAccessDecision.accessDenied();
      }
    }
    patientQueries.add(queryBuilder(patientSet, "Patient/", ","));
    return new NoOpAccessDecision(serverListIncludesAllPatients(patientQueries));
  }

  @Nullable
  private AccessDecision checkPatientAccessInUpdate(RequestDetailsReader requestDetails)
      throws IOException {
    String patientId = FhirUtil.getIdOrNull(requestDetails);
    if (patientId == null || !FhirUtil.isValidId(patientId)) {
      // This is an invalid PUT/PATCH request; note we are not supporting "conditional updates" for
      // Patient resources.
      logger.error("The provided Patient resource has no ID or it is invalid; denying access!");
      return NoOpAccessDecision.accessDenied();
    }
    if (patientsExist(patientId)) {
      logger.info("Updating existing patient {}, so no need to update access list.", patientId);
      return new NoOpAccessDecision(serverListIncludesAnyPatient(Sets.newHashSet(patientId)));
    }
    // Reaching here means a new Patient being created.
    return null;
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) throws IOException {
    BundlePatients patientRequestsInBundle = createBundlePatients(requestDetails);

    if (patientRequestsInBundle == null) {
      return NoOpAccessDecision.accessDenied();
    }

    boolean createPatients = patientRequestsInBundle.areTherePatientToCreate();
    Set<String> putPatientIds = patientRequestsInBundle.getUpdatedPatients();

    if (!createPatients && putPatientIds.isEmpty()) {
      return NoOpAccessDecision.accessGranted();
    }

    if (putPatientIds.isEmpty()) {
      return AccessGrantedAndUpdateList.forBundle(
          patientListId, httpFhirClient, fhirContext, Sets.newHashSet());
    } else {
      return AccessGrantedAndUpdateList.forBundle(
          patientListId, httpFhirClient, fhirContext, putPatientIds);
    }
  }

  @Nullable
  private BundlePatients createBundlePatients(RequestDetailsReader requestDetails)
      throws IOException {
    BundlePatients patientsInBundleUnfiltered = patientFinder.findPatientsInBundle(requestDetails);

    if (patientsInBundleUnfiltered == null) {
      return null;
    }

    BundlePatientsBuilder builder = new BundlePatientsBuilder();
    builder.setPatientCreationFlag(patientsInBundleUnfiltered.areTherePatientToCreate());

    Set<String> patientsToCreate = Sets.newHashSet();
    Set<String> patientsToUpdate = Sets.newHashSet();

    for (String patientId : patientsInBundleUnfiltered.getUpdatedPatients()) {
      if (!patientsExist(patientId)) {
        patientsToCreate.add(patientId);
      } else {
        patientsToUpdate.add(patientId);
      }
    }

    if (!patientsToCreate.isEmpty()) {
      builder.setPatientCreationFlag(true);
    }

    Set<String> patientQueries = Sets.newHashSet();
    for (Set<String> patientRefSet : patientsInBundleUnfiltered.getReferencedPatients()) {
      if (Collections.disjoint(patientRefSet, patientsToCreate)) {
        String orQuery = queryBuilder(patientRefSet, "Patient/", ",");
        patientQueries.add(orQuery);
      }
    }

    if (!patientsToUpdate.isEmpty()) {
      for (String eachPatient : patientsToUpdate) {
        String andQuery = String.format("Patient/%s", eachPatient);
        patientQueries.add(andQuery);
      }
    }

    if (!patientQueries.isEmpty() && !serverListIncludesAllPatients(patientQueries)) {
      logger.error("Reference Patients not in List!");
      return null;
    }

    return builder.addUpdatePatients(patientsToUpdate).build();
  }

  private String queryBuilder(Set<String> patientSet, String prefix, String delimiter) {
    return patientSet.stream()
        .filter(Objects::nonNull)
        .filter(Predicate.not(String::isEmpty))
        .map(p -> prefix + PARAM_ESCAPER.escape(p))
        .collect(Collectors.joining(delimiter));
  }

  @Named(value = "list")
  public static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String PATIENT_LIST_CLAIM = "patient_list";

    private String getListId(DecodedJWT jwt) {
      return FhirUtil.checkIdOrFail(JwtUtil.getClaimOrDie(jwt, PATIENT_LIST_CLAIM));
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      String patientListId = getListId(jwt);
      return new ListAccessChecker(httpFhirClient, patientListId, fhirContext, patientFinder);
    }
  }
}
