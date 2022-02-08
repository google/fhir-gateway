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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.util.UrlUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.fhir.proxy.BundlePatients.BundlePatientsBuilder;
import com.google.fhir.proxy.BundlePatients.BundlePatientsBuilder.PatientOp;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CompartmentDefinition;
import org.hl7.fhir.r4.model.CompartmentDefinition.CompartmentDefinitionResourceComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatientFinder {
  private static final Logger logger = LoggerFactory.getLogger(PatientFinder.class);
  private static PatientFinder instance = null;

  private final IFhirPath fhirPath;
  private final Map<String, List<String>> patientSearchParams;
  private final Map<String, List<String>> patientFhirPaths;
  private final FhirContext fhirContext;

  // This is supposed to be instantiated with getInstance method only.
  private PatientFinder(
      FhirContext fhirContext,
      Map<String, List<String>> patientFhirPaths,
      Map<String, List<String>> patientSearchParams) {
    this.fhirContext = fhirContext;
    this.fhirPath = fhirContext.newFhirPath();
    this.patientFhirPaths = patientFhirPaths;
    this.patientSearchParams = patientSearchParams;
  }

  @Nullable
  private String findPatientIdFromParams(
      String resourceName, Map<String, String[]> queryParameters) {
    List<String> searchParams = patientSearchParams.get(resourceName);
    if (searchParams != null) {
      // TODO make sure that complexities in FHIR search spec (like `_revinclude`) does not
      // cause unauthorized access issues here; maybe we should restrict search queries further.
      for (String param : searchParams) {
        String[] paramValues = queryParameters.get(param);
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
    return null;
  }

  @Nullable
  private String findPatientId(BundleEntryRequestComponent requestComponent)
      throws URISyntaxException {
    String patientId = null;
    if (requestComponent.getUrl() != null) {
      URI resourceUri = new URI(requestComponent.getUrl());
      IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
      if (FhirUtil.isSameResourceType(referenceElement.getResourceType(), ResourceType.Patient)) {
        return referenceElement.getIdPart();
      }
      if (referenceElement.getResourceType() == null) {
        Map<String, String[]> queryParams = UrlUtil.parseQueryString(resourceUri.getQuery());
        patientId = findPatientIdFromParams(resourceUri.getPath(), queryParams);
      }
    }
    if (patientId == null) {
      logger.warn("Patient ID cannot be found in " + requestComponent.getUrl());
    }
    return patientId;
  }

  /**
   * Finds the patient ID from the query if it is a direct Patient fetch (i.e., /Patient/PID) or the
   * patient can be inferred from query parameters.
   *
   * @param requestDetails the request
   * @return the id of the patient that this query belongs to or null if it cannot be inferred.
   */
  @Nullable
  String findPatientId(RequestDetails requestDetails) {
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
    if (FhirUtil.getIdOrNull(requestDetails) != null) {
      // Block any direct, non-patient resource fetches (e.g. Encounter/EID).
      // Since it is specifying a resource directly, we cannot know if this belongs to an
      // authorized patient.
      return null;
    }
    String patientId = findPatientIdFromParams(resourceName, requestDetails.getParameters());
    if (patientId == null) {
      logger.warn("Patient ID cannot be found in " + requestDetails.getCompleteUrl());
      return null;
    }
    return patientId;
  }

  private IBaseResource createResourceFromRequest(RequestDetails request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    return jsonParser.parseResource(requestContent);
  }

  private Set<String> parseReferencesForPatientIds(IBaseResource resource) {
    List<String> fhirPaths = patientFhirPaths.get(resource.fhirType());
    if (fhirPaths == null) {
      return Sets.newHashSet();
    }
    Set<String> patientIds = Sets.newHashSet();
    for (String path : fhirPaths) {
      List<Reference> refs = fhirPath.evaluate(resource, path, Reference.class);
      patientIds.addAll(
          refs.stream()
              .filter(
                  r ->
                      FhirUtil.isSameResourceType(
                          r.getReferenceElement().getResourceType(), ResourceType.Patient))
              .map(r -> r.getReferenceElement().getIdPart())
              .collect(Collectors.toList()));
    }
    return patientIds;
  }

  @Nullable
  BundlePatients findPatientsInBundle(RequestDetails request) {
    Bundle bundle = (Bundle) createResourceFromRequest(request);

    if (bundle.getType() != BundleType.TRANSACTION) {
      // Currently, support only for transaction bundles (b/217392030)
      logger.error("Bundle type needs to be transaction");
      return null;
    }

    BundlePatientsBuilder builder = new BundlePatientsBuilder();
    try {

      for (BundleEntryComponent entryComponent : bundle.getEntry()) {
        HTTPVerb httpMethod = entryComponent.getRequest().getMethod();
        switch (httpMethod) {
          case GET:
            processGet(entryComponent, builder);
            break;
          case POST:
            processPost(entryComponent, builder);
            break;
          case PUT:
            processPut(entryComponent, builder);
            break;
          default:
            ExceptionUtil.throwRuntimeExceptionAndLog(
                logger,
                String.format("HTTP request method %s is not supported!", httpMethod),
                ForbiddenOperationException.class);
        }
      }
      return builder.build();
    } catch (NullPointerException
        | ForbiddenOperationException
        | ClassCastException
        | URISyntaxException e) {
      logger.error("Exception while processing Bundle; denying access! ", e);
      return null;
    }
  }

  private void processGet(BundleEntryComponent entryComponent, BundlePatientsBuilder builder)
      throws URISyntaxException {
    // Ignore body content and just look at request (b/217237806)
    String patientId = findPatientId(entryComponent.getRequest());
    builder.addPatient(PatientOp.READ, patientId);
  }

  private void processPut(BundleEntryComponent entryComponent, BundlePatientsBuilder builder)
      throws URISyntaxException {
    Resource resource = entryComponent.getResource();
    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
      String patientId = findPatientId(entryComponent.getRequest());
      builder.addPatient(PatientOp.UPDATE, patientId);
    } else {
      addPatientReference(resource, builder);
    }
  }

  private void processPost(BundleEntryComponent entryComponent, BundlePatientsBuilder builder) {
    Resource resource = entryComponent.getResource();
    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
      builder.setPatientCreationFlag(true);
    } else {
      addPatientReference(resource, builder);
    }
  }

  private void addPatientReference(Resource resource, BundlePatientsBuilder builder) {
    Set<String> referencePatientIds = parseReferencesForPatientIds(resource);
    if (referencePatientIds.isEmpty()) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Patient reference must exist in resource", ForbiddenOperationException.class);
    }
    builder.addReferencedPatients(referencePatientIds);
  }

  Set<String> findPatientsInResource(RequestDetails request) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!resource.fhirType().equals(request.getResourceName())) {
      logger.error(
          "The provided resource is different from what is on the path; stopping parsing.");
      return Sets.newHashSet();
    }
    return parseReferencesForPatientIds(resource);
  }

  // A singleton instance of this class should be used, hence the constructor is private.
  static synchronized PatientFinder getInstance(FhirContext fhirContext) {
    if (instance != null) {
      return instance;
    }
    // Read patient compartment and create search param map.
    CompartmentDefinition patientCompartment;
    IParser jsonParser = fhirContext.newJsonParser();
    String compartmentText = readResource("CompartmentDefinition-patient.json");
    IBaseResource resource = jsonParser.parseResource(compartmentText);
    Preconditions.checkArgument(
        FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.CompartmentDefinition));
    patientCompartment = (CompartmentDefinition) resource;
    logger.info("Patient compartment is based on: " + patientCompartment);
    final Map<String, List<String>> patientSearchParams = Maps.newHashMap();
    makeSearchParamMap(patientCompartment, patientSearchParams);

    // Read FHIR paths for finding associated patients of each resource.
    String pathsJson = readResource("patient_paths.json");
    Gson gson = new Gson();
    final Map<String, List<String>> patientFhirPaths = gson.fromJson(pathsJson, Map.class);
    instance = new PatientFinder(fhirContext, patientFhirPaths, patientSearchParams);
    return instance;
  }

  private static String readResource(String resourcePath) {
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

  private static void makeSearchParamMap(
      CompartmentDefinition patientCompartment,
      final Map<String, List<String>> patientSearchParams) {
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
}
