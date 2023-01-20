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
package com.google.fhir.gateway;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.UrlUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.fhir.gateway.BundlePatients.BundlePatientsBuilder;
import com.google.fhir.gateway.BundlePatients.BundlePatientsBuilder.PatientOp;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.hl7.fhir.r4.model.Binary;
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

public final class PatientFinderImp implements PatientFinder {
  private static final Logger logger = LoggerFactory.getLogger(PatientFinderImp.class);
  private static PatientFinderImp instance = null;
  private static final String PATCH_OPERATION = "op";
  private static final String PATCH_OP_REPLACE = "replace";
  private static final String PATCH_OP_ADD = "add";
  private static final String PATCH_VALUE = "value";
  private static final String PATCH_PATH = "path";

  private final IFhirPath fhirPath;
  private final Map<String, List<String>> patientSearchParams;
  private final Map<String, List<String>> patientFhirPaths;
  private final FhirContext fhirContext;
  private final boolean blockJoins;

  // This is supposed to be instantiated with getInstance method only.
  private PatientFinderImp(
      FhirContext fhirContext,
      Map<String, List<String>> patientFhirPaths,
      Map<String, List<String>> patientSearchParams,
      boolean blockJoins) {
    this.fhirContext = fhirContext;
    this.fhirPath = fhirContext.newFhirPath();
    this.patientFhirPaths = patientFhirPaths;
    this.patientSearchParams = patientSearchParams;
    this.blockJoins = blockJoins;
  }

  @Nullable
  private String checkParamsAndFindPatientId(
      String resourceName, Map<String, String[]> queryParameters) {
    checkFhirJoinParams(queryParameters);
    List<String> searchParams = patientSearchParams.get(resourceName);
    if (searchParams != null) {
      for (String param : searchParams) {
        String[] paramValues = queryParameters.get(param);
        // We ignore if multiple search parameters match compartment definition.
        if (paramValues != null && paramValues.length == 1) {
          // Making sure that we extract the actual ID without any resource type or URL.
          IIdType id = new IdDt(paramValues[0]);
          // TODO add test for null/non-Patient cases.
          if (id.getResourceType() == null || id.getResourceType().equals("Patient")) {
            return FhirUtil.checkIdOrFail(id.getIdPart());
          }
        }
      }
    }
    return null;
  }

  private void checkFhirJoinParams(Map<String, String[]> queryParams) {
    // TODO decide whether to expose `blockJoins` as a configuration parameter or not. If we want to
    // expose this and make it more customizable (e.g., what joins to accept and what to reject) or
    // add more parameter sanity-checking, we should move this join logic to a separate class.
    if (blockJoins) {
      for (String queryParam : queryParams.keySet()) {
        // This follows the pattern in `QualifierDetails` of HAPI to match params chaining rules.
        if (queryParam.indexOf('.') >= 0) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger,
              "Search with chaining is blocked in param: " + queryParam,
              InvalidRequestException.class);
        }
        // Other "joins"
        if (queryParam.equals("_has")
            || queryParam.equals("_include")
            || queryParam.equals("_revinclude")) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger,
              String.format("Search with %s is blocked!", queryParam),
              InvalidRequestException.class);
        }
      }
    }
  }

  private String findPatientId(BundleEntryRequestComponent requestComponent)
      throws URISyntaxException {
    String patientId = null;
    if (requestComponent.getUrl() != null) {
      URI resourceUri = new URI(requestComponent.getUrl());
      IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
      if (FhirUtil.isSameResourceType(referenceElement.getResourceType(), ResourceType.Patient)) {
        return FhirUtil.checkIdOrFail(referenceElement.getIdPart());
      }
      if (referenceElement.getResourceType() == null) {
        Map<String, String[]> queryParams = UrlUtil.parseQueryString(resourceUri.getQuery());
        patientId = checkParamsAndFindPatientId(resourceUri.getPath(), queryParams);
      }
    }
    if (patientId == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "Patient ID cannot be found in " + requestComponent.getUrl(),
          InvalidRequestException.class);
    }
    return patientId;
  }

  @Override
  public String findPatientFromParams(RequestDetailsReader requestDetails) {
    String resourceName = requestDetails.getResourceName();
    if (resourceName == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "No resource specified for request " + requestDetails.getRequestPath(),
          InvalidRequestException.class);
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
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "Direct resource fetch is only supported for Patient; use search for " + resourceName,
          InvalidRequestException.class);
      return null;
    }
    Map<String, String[]> queryParams = requestDetails.getParameters();
    String patientId = checkParamsAndFindPatientId(resourceName, queryParams);
    if (patientId == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "Patient ID cannot be found in " + requestDetails.getCompleteUrl(),
          InvalidRequestException.class);
    }
    return patientId;
  }

  private IBaseResource createResourceFromRequest(RequestDetailsReader request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    return jsonParser.parseResource(requestContent);
  }

  private JsonArray createJsonArrayFromRequest(RequestDetailsReader request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    return JsonParser.parseString(requestContent).getAsJsonArray();
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
              .map(r -> FhirUtil.checkIdOrFail(r.getReferenceElement().getIdPart()))
              .collect(Collectors.toList()));
    }
    return patientIds;
  }

  @Override
  public BundlePatients findPatientsInBundle(RequestDetailsReader request) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!(resource instanceof Bundle)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "The provided resource is not a Bundle!", InvalidRequestException.class);
    }
    Bundle bundle = (Bundle) resource;

    if (bundle.getType() != BundleType.TRANSACTION) {
      // Currently, support only for transaction bundles; see:
      //   https://github.com/google/fhir-access-proxy/issues/67
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Bundle type needs to be transaction!", InvalidRequestException.class);
    }

    BundlePatientsBuilder builder = new BundlePatientsBuilder();
    if (!bundle.hasEntry()) {
      return builder.build();
    }

    try {
      for (BundleEntryComponent entryComponent : bundle.getEntry()) {
        HTTPVerb httpMethod = entryComponent.getRequest().getMethod();
        if (httpMethod != HTTPVerb.GET && !entryComponent.hasResource()) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger, "Bundle entry requires a resource field!", InvalidRequestException.class);
        }
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
          case PATCH:
            processPatch(entryComponent, builder);
            break;
          default:
            ExceptionUtil.throwRuntimeExceptionAndLog(
                logger,
                String.format("HTTP request method %s is not supported!", httpMethod),
                InvalidRequestException.class);
        }
      }
      return builder.build();
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error parsing URI in Bundle!", e, InvalidRequestException.class);
    }
    // It should never reach here!
    return null;
  }

  @Nullable
  private String parsePatchForPatientId(JsonObject patch, String resourceName) {
    if (patch.get(PATCH_PATH) == null || patch.get(PATCH_OPERATION) == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Invalid patch!", InvalidRequestException.class);
    }
    List<String> fhirPaths = patientFhirPaths.get(resourceName);
    if (fhirPaths == null) {
      return null;
    }
    boolean containsPatientCompartment =
        fhirPaths.stream()
            .map(r -> String.format("/%s", r))
            .anyMatch(patch.get(PATCH_PATH).getAsString()::startsWith);
    if (!containsPatientCompartment) {
      return null;
    }

    if (patch.get(PATCH_OPERATION).getAsString().equals(PATCH_OP_REPLACE)
        || patch.get(PATCH_OPERATION).getAsString().equals(PATCH_OP_ADD)) {
      JsonElement valueField = patch.get(PATCH_VALUE);

      Reference reference = null;
      if (valueField.isJsonArray() && !valueField.getAsJsonArray().isEmpty()) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger,
            "non-empty JsonArray in 'value' for Patient Compartment is not supported!",
            InvalidRequestException.class);
      }

      if (valueField.isJsonObject() && valueField.getAsJsonObject().has("reference")) {
        reference = new Reference(valueField.getAsJsonObject().get("reference").getAsString());
      }

      if (valueField.isJsonPrimitive()
          && patch.get(PATCH_PATH).getAsString().contains("/reference")) {
        reference = new Reference(valueField.getAsString());
      }

      if (reference == null) {
        return null;
      }

      if (!FhirUtil.isSameResourceType(
          reference.getReferenceElement().getResourceType(), ResourceType.Patient)) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "Expected patient reference!", InvalidRequestException.class);
      }

      return FhirUtil.checkIdOrFail(reference.getReferenceElement().getIdPart());
    }

    ExceptionUtil.throwRuntimeExceptionAndLog(
        logger,
        String.format(
            "%s operation on Patient Compartment is not supported!",
            patch.get(PATCH_OPERATION).getAsString()),
        InvalidRequestException.class);
    return null;
  }

  private void processGet(BundleEntryComponent entryComponent, BundlePatientsBuilder builder)
      throws URISyntaxException {
    // Ignore body content and just look at request.
    String patientId = findPatientId(entryComponent.getRequest());
    builder.addPatient(PatientOp.READ, patientId);
  }

  private void processPatch(BundleEntryComponent entryComponent, BundlePatientsBuilder builder)
      throws URISyntaxException {

    // Find patient id in request.url
    String patientId = findPatientId(entryComponent.getRequest());
    String resourceType =
        new Reference(entryComponent.getResource().getId()).getReferenceElement().getResourceType();
    if (FhirUtil.isSameResourceType(resourceType, ResourceType.Patient)) {
      builder.addPatient(PatientOp.UPDATE, patientId);
    } else {
      builder.addReferencedPatients(Sets.newHashSet(patientId));
    }
    // Find patient ids in body
    if (!FhirUtil.isSameResourceType(
        entryComponent.getResource().fhirType(), ResourceType.Binary)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "PATCH resource type must be Binary", InvalidRequestException.class);
    }

    Binary binaryResource = (Binary) entryComponent.getResource();
    if (!binaryResource.getContentType().equals(Constants.CT_JSON_PATCH)) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format("PATCH content type must be %s", Constants.CT_JSON_PATCH),
          InvalidRequestException.class);
    }

    JsonArray jsonArray =
        JsonParser.parseString(new String(binaryResource.getData())).getAsJsonArray();
    Set<String> patientsInPatch = parseJsonArrayForPatch(jsonArray, resourceType);
    if (!patientsInPatch.isEmpty()) {
      builder.addReferencedPatients(patientsInPatch);
    }
  }

  private void processPut(BundleEntryComponent entryComponent, BundlePatientsBuilder builder)
      throws URISyntaxException {
    Resource resource = entryComponent.getResource();
    String patientId = findPatientId(entryComponent.getRequest());
    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
      builder.addPatient(PatientOp.UPDATE, patientId);
    } else {
      builder.addReferencedPatients(Sets.newHashSet(patientId));
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
          logger, "Patient reference must exist in resource", InvalidRequestException.class);
    }
    builder.addReferencedPatients(referencePatientIds);
  }

  @Override
  public Set<String> findPatientsInResource(RequestDetailsReader request) {
    IBaseResource resource = createResourceFromRequest(request);
    if (!resource.fhirType().equals(request.getResourceName())) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          String.format(
              "The provided resource %s is different from what is on the path: %s ",
              resource.fhirType(), request.getResourceName()),
          InvalidRequestException.class);
    }
    return parseReferencesForPatientIds(resource);
  }

  @Override
  public Set<String> findPatientsInPatch(RequestDetailsReader request, String resourceName) {
    JsonArray jsonArray = createJsonArrayFromRequest(request);
    return parseJsonArrayForPatch(jsonArray, resourceName);
  }

  private Set<String> parseJsonArrayForPatch(JsonArray jsonArray, String resourceName) {
    Set<String> patientIds = Sets.newHashSet();
    for (JsonElement jsonElement : jsonArray) {
      String patientId = parsePatchForPatientId(jsonElement.getAsJsonObject(), resourceName);
      if (patientId != null) {
        patientIds.add(patientId);
      }
    }
    return patientIds;
  }

  // A singleton instance of this class should be used, hence the constructor is private.
  public static synchronized PatientFinderImp getInstance(FhirContext fhirContext) {
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
    instance = new PatientFinderImp(fhirContext, patientFhirPaths, patientSearchParams, true);
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
