package com.google.fhir.proxy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CompartmentDefinition;
import org.hl7.fhir.r4.model.CompartmentDefinition.CompartmentDefinitionResourceComponent;
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
  private static final String PATIENT_LIST_CLAIM = "patient_list";
  private final FhirContext fhirContext;
  private final HttpFhirClient httpFhirClient;
  private final String patientListId;
  private final Map<String, List<String>> patientSearchParams;

  private ListAccessChecker(
      HttpFhirClient httpFhirClient,
      String patientListId,
      FhirContext fhirContext,
      Map<String, List<String>> patientSearchParams) {
    this.fhirContext = fhirContext;
    this.httpFhirClient = httpFhirClient;
    this.patientListId = patientListId;
    this.patientSearchParams = patientSearchParams;
  }

  private static boolean isSameResourceType(String resourceType, ResourceType type) {
    return ResourceType.fromCode(resourceType) == type;
  }

  private boolean serverListIncludesPatient(String patientId) {
    if (patientId == null) {
      return false;
    }
    // TODO consider using HAPI FHIR clients to avoid crafting search queries and parsing responses.
    // We cannot use `_summary` parameter because it is not implemented on GCP yet; so to prevent a
    // potentially huge list to be fetched each time, we add `_elements=id`.
    String searchQuery =
        String.format("/List?_id=%s&item=Patient/%s&_elements=id", this.patientListId, patientId);
    try {
      HttpResponse httpResponse = httpFhirClient.getResource(searchQuery);
      HttpUtil.validateResponseEntityOrFail(httpResponse, searchQuery);
      IParser jsonParser = fhirContext.newJsonParser();
      IBaseResource resource = jsonParser.parseResource(httpResponse.getEntity().getContent());
      Preconditions.checkArgument(isSameResourceType(resource.fhirType(), ResourceType.Bundle));
      Bundle bundle = (Bundle) resource;
      // We expect exactly one result which is `patientListId`.
      return bundle.getTotal() == 1;
    } catch (IOException e) {
      logger.error("Exception while accessing " + searchQuery, e);
    }
    return false;
  }

  private String getIdOrNull(RequestDetails requestDetails) {
    if (requestDetails.getId() == null) {
      return null;
    }
    return requestDetails.getId().getIdPart();
  }

  @VisibleForTesting
  @Nullable
  String findPatientId(RequestDetails requestDetails) {
    String resourceName = requestDetails.getResourceName();
    if (resourceName == null) {
      logger.error("No resource specified for request " + requestDetails.getRequestPath());
      return null;
    }
    // Note we only let fetching data for one patient in each query; we may want to revisit this
    // if we need to batch multiple patients together in one query.
    if (isSameResourceType(resourceName, ResourceType.Patient)) {
      return getIdOrNull(requestDetails);
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
          // TODO do some sanity checks on the returned value (b/207737513).
          return id.getIdPart();
        }
      }
    }
    logger.warn("Patient ID cannot be found in " + requestDetails.getCompleteUrl());
    return null;
  }

  /**
   * Inspects the given request to make sure that it is for a FHIR resource of a patient that the
   * current user has access too; i.e., the patient is in the patient-list associated to the user.
   *
   * @param requestDetails the original request sent to the proxy.
   * @return true iff patient is in the patient-list associated to the current user.
   */
  @Override
  public boolean canAccess(RequestDetails requestDetails) {
    if (requestDetails.getRequestType() == RequestTypeEnum.GET) {
      // There should be a patient id in search params; the param name is based on the resource.
      if (isSameResourceType(requestDetails.getResourceName(), ResourceType.List)) {
        if (patientListId.equals(getIdOrNull(requestDetails))) {
          return true;
        }
      }
      String patientId = findPatientId(requestDetails);
      return serverListIncludesPatient(patientId);
    } else if (requestDetails.getRequestType() == RequestTypeEnum.PUT) {
      if (!isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
        // TODO add parsing of non-Patient resources to extract patientId and check authorization.
        return false;
      }
      String patientId = getIdOrNull(requestDetails);
      return serverListIncludesPatient(patientId);
    } else {
      // TODO decide what to do for other methods like POST (b/207589782).
      return false;
    }
  }

  public static class Factory implements AccessCheckerFactory {

    private final FhirContext fhirContext;
    private final CompartmentDefinition patientCompartment;
    private final Map<String, List<String>> patientSearchParams;

    public Factory(RestfulServer server) {
      this.fhirContext = server.getFhirContext();
      IParser jsonParser = fhirContext.newJsonParser();
      String compartmentText = null;
      try {
        URL url =
            server
                .getServletContext()
                .getResource("/WEB-INF/classes/CompartmentDefinition-patient.json");
        logger.info("Loading patient compartment definition from " + url);
        compartmentText = IOUtils.toString(url, StandardCharsets.UTF_8);
      } catch (IOException e) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger,
            "Cannot read CompartmentDefinition-patient.json resource!",
            e,
            ForbiddenOperationException.class);
      }
      IBaseResource resource = jsonParser.parseResource(compartmentText);
      Preconditions.checkArgument(
          ListAccessChecker.isSameResourceType(
              resource.fhirType(), ResourceType.CompartmentDefinition));
      this.patientCompartment = (CompartmentDefinition) resource;
      logger.info("Patient compartment is based on: " + patientCompartment);
      this.patientSearchParams = Maps.newHashMap();
      makeSearchParamMap();
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

    @Override
    public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      Claim patientListClaim = jwt.getClaim(PATIENT_LIST_CLAIM);
      if (patientListClaim == null) {
        throw new ForbiddenOperationException(
            String.format("The provided token has no %s claim!", PATIENT_LIST_CLAIM));
      }
      // TODO do some sanity checks on the `patientListId` (b/207737513).
      String patientListId = patientListClaim.asString();
      return new ListAccessChecker(httpFhirClient, patientListId, fhirContext, patientSearchParams);
    }
  }
}
