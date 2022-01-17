/*
 * Copyright 2021 Google LLC
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
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CompartmentDefinition;
import org.hl7.fhir.r4.model.CompartmentDefinition.CompartmentDefinitionResourceComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_list` ID in the access token to fetch the "List" of patient
 * IDs that the given user has access to.
 */
public class ListAccessChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(ListAccessChecker.class);
  static final String PATIENT_LIST_CLAIM = "patient_list";
  private final FhirContext fhirContext;
  private final IFhirPath fhirPath;
  private final HttpFhirClient httpFhirClient;
  private final String patientListId;
  private final Map<String, List<String>> patientSearchParams;
  private final Map<String, List<String>> patientFhirPaths;

  private ListAccessChecker(
      HttpFhirClient httpFhirClient,
      String patientListId,
      FhirContext fhirContext,
      Map<String, List<String>> patientSearchParams,
      Map<String, List<String>> patientFhirPaths) {
    this.fhirContext = fhirContext;
    this.fhirPath = fhirContext.newFhirPath();
    this.httpFhirClient = httpFhirClient;
    this.patientListId = patientListId;
    this.patientSearchParams = patientSearchParams;
    this.patientFhirPaths = patientFhirPaths;
  }

  // Note this returns true iff at least one of the patient IDs is found in the associated list.
  // The rationale is that a user should have access to a resource iff they are authorized to access
  // at least one of the patients referenced in that resource. This is a subjective decision, so we
  // may want to revisit it in the future.
  private boolean serverListIncludesAnyPatient(List<String> patientIds) {
    if (patientIds == null) {
      return false;
    }
    // TODO consider using the HAPI FHIR client instead (b/211231483).
    String patientParam =
        patientIds.stream()
            .filter(Objects::nonNull)
            .map(p -> "Patient/" + p)
            .collect(Collectors.joining(","));
    if (patientParam.isEmpty()) {
      return false;
    }
    // We cannot use `_summary` parameter because it is not implemented on GCP yet; so to prevent a
    // potentially huge list to be fetched each time, we add `_elements=id`.
    String searchQuery =
        String.format("/List?_id=%s&item=%s&_elements=id", this.patientListId, patientParam);
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

  @Nullable
  private String findPatientId(RequestDetails requestDetails) {
    String resourceName = requestDetails.getResourceName();
    if (resourceName == null) {
      logger.error("No resource specified for request " + requestDetails.getRequestPath());
      return null;
    }
    // Note we only let fetching data for one patient in each query; we may want to revisit this
    // if we need to batch multiple patients together in one query.
    if (FhirUtil.isSameResourceType(resourceName, ResourceType.Patient)) {
      return FhirUtil.getIdOrNull(requestDetails);
    }
    List<String> searchParams = patientSearchParams.get(resourceName);
    if (searchParams != null) {
      // TODO make sure that complexities in FHIR search spec (like `_revinclude`) does not
      // cause unauthorized access issues here; maybe we should restrict search queries further.
      for (String param : searchParams) {
        String[] paramValues = requestDetails.getParameters().get(param);
        // We ignore if multiple search parameters match compartment definition.
        if (paramValues != null && paramValues.length == 1) {
          // Making sure that we extract the actual ID without any resource type or URL.
          IIdType id = new IdDt(paramValues[0]);
          // TODO add test for null/non-Patient cases.
          if (id.getResourceType() == null || id.getResourceType().equals("Patient")) {
            // TODO do some sanity checks on the returned value (b/207737513).
            return id.getIdPart();
          }
        }
      }
    }
    logger.warn("Patient ID cannot be found in " + requestDetails.getCompleteUrl());
    return null;
  }

  private List<String> findPatientsInResource(RequestDetails request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    IBaseResource resource = jsonParser.parseResource(requestContent);
    if (!resource.fhirType().equals(request.getResourceName())) {
      // The provided resource is different from what is on the path; stop parsing.
      return Lists.newArrayList();
    }
    List<String> fhirPaths = patientFhirPaths.get(resource.fhirType());
    if (fhirPaths == null) {
      return Lists.newArrayList();
    }
    List<String> patiendIds = Lists.newArrayList();
    for (String path : fhirPaths) {
      List<Reference> refs = fhirPath.evaluate(resource, path, Reference.class);
      patiendIds.addAll(
          refs.stream()
              .filter(r -> "Patient".equals(r.getReferenceElement().getResourceType()))
              .map(r -> r.getReferenceElement().getIdPart())
              .collect(Collectors.toList()));
    }
    return patiendIds;
  }

  private boolean patientExists(String patientId) throws IOException {
    // TODO consider using the HAPI FHIR client instead (b/211231483).
    String searchQuery = String.format("/Patient?_id=%s&_elements=id", patientId);
    HttpResponse response = httpFhirClient.getResource(searchQuery);
    Bundle bundle = FhirUtil.parseResponseToBundle(fhirContext, response);
    if (bundle.getTotal() > 1) {
      logger.error(
          String.format(
              "%s patients with the same ID %s returned from the FHIR store.",
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
  public AccessDecision checkAccess(RequestDetails requestDetails) {
    if (requestDetails.getRequestType() == RequestTypeEnum.GET) {
      // There should be a patient id in search params; the param name is based on the resource.
      if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.List)) {
        if (patientListId.equals(FhirUtil.getIdOrNull(requestDetails))) {
          return NoOpAccessDecision.accessGranted();
        }
        return NoOpAccessDecision.accessDenied();
      }
      String patientId = findPatientId(requestDetails);
      return new NoOpAccessDecision(serverListIncludesAnyPatient(Lists.newArrayList(patientId)));
    }
    if (requestDetails.getRequestType() == RequestTypeEnum.PUT
        || requestDetails.getRequestType() == RequestTypeEnum.POST) {
      if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
        String patientId = FhirUtil.getIdOrNull(requestDetails);
        if (requestDetails.getRequestType() == RequestTypeEnum.PUT) {
          if (patientId == null) {
            // This is an invalid PUT request; note we are not supporting "conditional updates".
            logger.error("The provided Patient resource has no ID; denying access!");
            return NoOpAccessDecision.accessDenied();
          }
          try {
            if (patientExists(patientId)) {
              logger.info(
                  "Updating existing patient {}, so no need to update access list.", patientId);
              return new NoOpAccessDecision(
                  serverListIncludesAnyPatient(Lists.newArrayList(patientId)));
            }
          } catch (IOException e) {
            logger.error("Exception while checking patient existence; denying access! ", e);
            return NoOpAccessDecision.accessDenied();
          }
        }
        // We have decided to let clients add new patients while understanding its security risks.
        return new AccessGrantedAndUpdateList(
            patientListId, httpFhirClient, fhirContext, patientId);
      } else {
        List<String> patientIds = findPatientsInResource(requestDetails);
        return new NoOpAccessDecision(serverListIncludesAnyPatient(patientIds));
      }
    }
    // TODO decide what to do for other methods like PATCH and DELETE.
    return NoOpAccessDecision.accessDenied();
  }

  public static class Factory implements AccessCheckerFactory {

    private final FhirContext fhirContext;
    private final CompartmentDefinition patientCompartment;
    private final Map<String, List<String>> patientSearchParams;
    private final Map<String, List<String>> patientFhirPaths;

    public Factory(RestfulServer server) {
      this.fhirContext = server.getFhirContext();

      // Read patient compartment and create search param map.
      IParser jsonParser = fhirContext.newJsonParser();
      String compartmentText = readResource("CompartmentDefinition-patient.json");
      IBaseResource resource = jsonParser.parseResource(compartmentText);
      Preconditions.checkArgument(
          FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.CompartmentDefinition));
      this.patientCompartment = (CompartmentDefinition) resource;
      logger.info("Patient compartment is based on: " + patientCompartment);
      this.patientSearchParams = Maps.newHashMap();
      makeSearchParamMap();

      // Read FHIR paths for finding associated patients of each resource.
      String pathsJson = readResource("patient_paths.json");
      Gson gson = new Gson();
      this.patientFhirPaths = gson.fromJson(pathsJson, Map.class);
    }

    private String readResource(String resourcePath) {
      try {
        // TODO make sure relative addresses are handled properly and that @Beta is okay.
        URL url = Resources.getResource(resourcePath);
        logger.info("Loading patient compartment definition from " + url);
        return Resources.toString(url, StandardCharsets.UTF_8);
      } catch (IOException e) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger,
            String.format("Cannot read %s resource!", resourcePath),
            e,
            ForbiddenOperationException.class);
        return null;
      }
    }

    private void makeSearchParamMap() {
      for (CompartmentDefinitionResourceComponent resource : patientCompartment.getResource()) {
        String resourceType = resource.getCode();
        if (resourceType == null) {
          logger.warn("Unexpected null-type resource in patient CompartmentDefinition!");
          continue;
        }
        List<String> paramList = Lists.newArrayList();
        for (StringType searchParam : resource.getParam()) {
          paramList.add(searchParam.toString());
        }
        patientSearchParams.put(resourceType, paramList);
      }
    }

    private String getListId(DecodedJWT jwt) {
      Claim patientListClaim = jwt.getClaim(PATIENT_LIST_CLAIM);
      if (patientListClaim == null) {
        throw new ForbiddenOperationException(
            String.format("The provided token has no %s claim!", PATIENT_LIST_CLAIM));
      }
      // TODO do some sanity checks on the `patientListId` (b/207737513).
      return patientListClaim.asString();
    }

    @Override
    public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      String patientListId = getListId(jwt);
      return new ListAccessChecker(
          httpFhirClient, patientListId, fhirContext, patientSearchParams, patientFhirPaths);
    }
  }
}
