/*
 * Copyright 2021-2025 Google LLC
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
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.storage.interceptor.balp.BalpConstants;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import ca.uhn.fhir.util.UrlUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Strings;
import com.google.fhir.gateway.interfaces.AuditEventHelper;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO unit test this class

/**
 * This class is meant to be immutable with a new instance created to process each individual
 * request.
 *
 * <p>This implementation is partially inspired by the HAPI FHIR Storage BalpAuditCaptureInterceptor
 * class. See
 * https://github.com/hapifhir/hapi-fhir/blob/v8.2.0/hapi-fhir-storage/src/main/java/ca/uhn/fhir/storage/interceptor/balp/BalpAuditCaptureInterceptor.java
 */
public class AuditEventHelperImpl implements AuditEventHelper {

  private static final Logger logger = LoggerFactory.getLogger(AuditEventHelperImpl.class);

  private final PatientFinderImp patientFinder;
  private final RequestDetailsReader requestDetailsReader;
  private final String responseContentLocation;
  private final Reference agentUserWho;
  private final DecodedJWT decodedJWT;
  private final Date periodStartTime;
  private final HttpFhirClient httpFhirClient;
  private final FhirContext fhirContext;
  private final boolean isPOSTBundle;
  private final IBaseResource requestResource;
  private final IBaseResource responseResource;
  private final IBaseResource auditEventSource;

  private AuditEventHelperImpl(
      RequestDetailsReader requestDetailsReader,
      String responseContent,
      String responseContentLocation,
      Reference agentUserWho,
      @Nullable DecodedJWT decodedJWT,
      Date periodStartTime,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext) {
    this.patientFinder = PatientFinderImp.getInstance(fhirContext);
    this.requestDetailsReader = requestDetailsReader;
    this.responseContentLocation = responseContentLocation;
    this.agentUserWho = agentUserWho;
    this.decodedJWT = decodedJWT;
    this.periodStartTime = periodStartTime;
    this.httpFhirClient = httpFhirClient;
    this.fhirContext = fhirContext;
    this.isPOSTBundle =
        requestDetailsReader.getRequestType() == RequestTypeEnum.POST
            && requestDetailsReader.getResourceName() == null;

    // The following constructor logic prepares the source for the AuditEvent to be generated. It
    // uses a combination of the request payload, response resource and the Content-Location header
    // to generate the most complete input resource. This input is mostly used to generate
    // AuditEvents for single resource requests as opposed to POST bundle requests which are handled
    // by the {@link #extractResourceFromBundleComponent()} method.

    requestResource =
        requestDetailsReader.loadRequestContents().length > 0
                && !RequestTypeEnum.PATCH.equals(
                    requestDetailsReader
                        .getRequestType()) // Should we consider moving these checks to the Util
            // function and return null otherwise?
            ? FhirUtil.createResourceFromRequest(fhirContext, requestDetailsReader)
            : null;

    responseResource = FhirUtil.parseResourceOrNull(fhirContext, responseContent);

    // This handles operations with no response body returned i.e. POST/PUT/PATCH requests with
    // Prefer: return=minimal HTTP header
    IBaseResource contentLocationResponseResource =
        responseContentLocation != null
            ? FhirUtil.parseResourceOrNull(
                fhirContext, getResourceFromContentLocation(responseContentLocation))
            : null;

    auditEventSource =
        createAuditEventSource(
            this.requestResource, this.responseResource, contentLocationResponseResource);
  }

  @Nullable
  private IBaseResource createAuditEventSource(
      IBaseResource requestResource,
      IBaseResource responseResource,
      IBaseResource contentLocationResponseResource) {
    IBaseResource auditEventSource;
    if (responseResource != null) {
      auditEventSource = responseResource;
    } else if (requestResource != null) {
      auditEventSource = requestResource;
      auditEventSource.setId(
          contentLocationResponseResource != null
              ? contentLocationResponseResource.getIdElement()
              : auditEventSource.getIdElement()); // POST requests don't have a valid id yet.
    } else {
      auditEventSource = contentLocationResponseResource;
    }
    return auditEventSource;
  }

  public static AuditEventHelper createInstance(
      RequestDetailsReader requestDetailsReader,
      String responseStringContent,
      String responseContentLocation,
      Reference agentUserWho,
      DecodedJWT decodedJWT,
      Date periodStartTime,
      HttpFhirClient fhirClient,
      FhirContext fhirContext) {
    return new AuditEventHelperImpl(
        requestDetailsReader,
        responseStringContent,
        responseContentLocation,
        agentUserWho,
        decodedJWT,
        periodStartTime,
        fhirClient,
        fhirContext);
  }

  @Override
  public void processAuditEvents() {
    List<AuditEvent> auditEventList = new ArrayList<>();

    Map<RestOperationTypeEnum, List<AuditEventBuilder.ResourceContext>> resourcesMap =
        extractFhirResourcesByRestOperationType();

    resourcesMap.forEach(
        (restOperationType, resources) -> {
          auditEventList.addAll(getAuditEvents(restOperationType, resources));
        });

    // TODO Investigate bulk saving (batch processing) instead to improve performance. We'll
    // probably need a mechanism for chunking e.g. 100, 200, or 500 batch
    for (AuditEvent auditEvent : auditEventList) {
      auditEvent.getPeriod().setEnd(new Date());
      try {
        org.apache.http.HttpResponse response = this.httpFhirClient.postResource(auditEvent);
        handleErrorResponse(response);
      } catch (IOException exception) {
        ExceptionUtil.throwRuntimeExceptionAndLog(logger, exception.getMessage(), exception);
      }
    }
  }

  private void handleErrorResponse(org.apache.http.HttpResponse response) throws IOException {
    if (response != null && !HttpUtil.isResponseValid(response)) {
      StringWriter responseStringWriter = new StringWriter();
      try (Reader reader = HttpUtil.readerFromEntity(response.getEntity())) {
        reader.transferTo(responseStringWriter);
        OperationOutcome outcome =
            (OperationOutcome)
                FhirUtil.parseResourceOrNull(this.fhirContext, responseStringWriter.toString());
        if (outcome != null)
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger, outcome.getIssueFirstRep().getDiagnostics());
      }
    }
  }

  private @Nonnull List<AuditEvent> getAuditEvents(
      RestOperationTypeEnum restOperationType, List<AuditEventBuilder.ResourceContext> resources) {
    List<AuditEvent> auditEventList = new ArrayList<>();
    switch (restOperationType) {
      case SEARCH_TYPE:
      case SEARCH_SYSTEM:
      case GET_PAGE:
        auditEventList =
            createAuditEventCore(
                restOperationType,
                resources,
                BalpProfileEnum.PATIENT_QUERY,
                BalpProfileEnum.BASIC_QUERY);
        break;

      case READ:
      case VREAD:
        auditEventList =
            createAuditEventCore(
                restOperationType,
                resources,
                BalpProfileEnum.PATIENT_READ,
                BalpProfileEnum.BASIC_READ);
        break;

      case CREATE:
        auditEventList =
            createAuditEventCore(
                restOperationType,
                resources,
                BalpProfileEnum.PATIENT_CREATE,
                BalpProfileEnum.BASIC_CREATE);
        break;

      case UPDATE:
        auditEventList =
            createAuditEventCore(
                restOperationType,
                resources,
                BalpProfileEnum.PATIENT_UPDATE,
                BalpProfileEnum.BASIC_UPDATE);
        break;

      case DELETE:

        // NOTE: The success of processing of DELETE is heavily dependent on server validation
        // policy e.g. If you have a permission list that references the resource being deleted,
        // it breaks the delete operation. Likewise if you reference the resource in AuditEvent
        // such as logging when you first created or updated it the operation breaks. In case of
        // such an error, AuditEvent.outcome and AuditEvent.outcomeDesc are populated accordingly

        // Also note, this implementation only fully captures that a specific resource was deleted
        // and by whom but not the compartment owner.
        // With no access to the actual deleted resource we can't get the compartment owner unless
        // the resource itself is a Patient. A crude way to get the owner would be to fetch the
        // actual resource from the database first before creating the AuditEvent.

        // This implementation skips logging Deletes if the operation is conditional (thus no
        // Resource ID present)

        auditEventList =
            createAuditEventCore(
                restOperationType,
                resources,
                BalpProfileEnum.PATIENT_DELETE,
                BalpProfileEnum.BASIC_DELETE);
        break;

      default:
        break;
    }
    return auditEventList;
  }

  private String getResourceTemplate(String resourceType, String resourceId) {
    return String.format("{ \"resourceType\": \"%s\", \"id\": \"%s\"}", resourceType, resourceId);
  }

  private List<AuditEvent> createAuditEventCore(
      RestOperationTypeEnum restOperationType,
      List<AuditEventBuilder.ResourceContext> resources,
      BalpProfileEnum patientProfile,
      BalpProfileEnum basicProfile) {
    List<AuditEvent> auditEventList = new ArrayList<>();
    for (AuditEventBuilder.ResourceContext resourceContext : resources) {

      IBaseResource iBaseResource = resourceContext.getResourceEntity();
      if (iBaseResource instanceof DomainResource) {

        DomainResource resource = (DomainResource) iBaseResource;
        Set<String> patientIds = patientFinder.findPatientIds(resource);

        if (!patientIds.isEmpty()) {
          if (resource instanceof OperationOutcome) {
            auditEventList.add(
                createAuditEventOperationOutcome(
                    restOperationType,
                    (OperationOutcome) resource,
                    patientProfile,
                    resourceContext.getQueryEntity()));
          } else {
            auditEventList.add(
                createAuditEventEHR(
                    restOperationType,
                    resource,
                    patientProfile,
                    patientIds,
                    resourceContext.getQueryEntity()));
          }

        } else {
          if (resource instanceof OperationOutcome) {
            auditEventList.add(
                createAuditEventOperationOutcome(
                    restOperationType,
                    (OperationOutcome) resource,
                    basicProfile,
                    resourceContext.getQueryEntity()));
          } else {
            auditEventList.add(
                createAuditEventEHR(
                    restOperationType,
                    resource,
                    basicProfile,
                    Set.of(),
                    resourceContext.getQueryEntity()));
          }
        }
      }
    }

    return auditEventList;
  }

  @Nullable
  private AuditEvent.AuditEventOutcome mapOutcomeErrorCode(
      OperationOutcome.IssueSeverity issueSeverity) {
    AuditEvent.AuditEventOutcome errorCode = null;
    if (OperationOutcome.IssueSeverity.FATAL.equals(issueSeverity)) {

      errorCode = AuditEvent.AuditEventOutcome._12;
    } else if (OperationOutcome.IssueSeverity.ERROR.equals(issueSeverity)) {

      errorCode = AuditEvent.AuditEventOutcome._8;
    } else if (OperationOutcome.IssueSeverity.WARNING.equals(issueSeverity)) {

      errorCode = AuditEvent.AuditEventOutcome._4;
    } else if (OperationOutcome.IssueSeverity.INFORMATION.equals(issueSeverity)) {

      errorCode = AuditEvent.AuditEventOutcome._0;
    }
    return errorCode;
  }

  private Map<RestOperationTypeEnum, List<AuditEventBuilder.ResourceContext>>
      extractFhirResourcesByRestOperationType() {
    Map<RestOperationTypeEnum, List<AuditEventBuilder.ResourceContext>>
        resourcesByRestOperationMap = new HashMap<>();

    AuditEventBuilder.QueryEntity queryEntity =
        AuditEventBuilder.QueryEntity.builder()
            .completeUrl(requestDetailsReader.getCompleteUrl())
            .requestType(requestDetailsReader.getRequestType())
            .fhirServerBase(requestDetailsReader.getFhirServerBase())
            .requestPath(requestDetailsReader.getRequestPath())
            .parameters(requestDetailsReader.getParameters())
            .build();

    if (auditEventSource instanceof Bundle) {
      List<Bundle.BundleEntryComponent> responseBundleEntryComponents =
          ((Bundle) auditEventSource).getEntry();

      List<Bundle.BundleEntryComponent> requestBundleEntryComponents =
          isPOSTBundle ? ((Bundle) requestResource).getEntry() : List.of();

      for (int i = 0; i < responseBundleEntryComponents.size(); i++) {

        IBaseResource bundleEntryComponentResource =
            extractResourceFromBundleComponent(
                !requestBundleEntryComponents.isEmpty()
                    ? requestBundleEntryComponents.get(i)
                    : null,
                responseBundleEntryComponents.get(i));

        AuditEventBuilder.QueryEntity nestedResourceQueryEntity = queryEntity;
        RestOperationTypeEnum restOperationType;
        if (isPOSTBundle) {

          nestedResourceQueryEntity =
              generateBundleEntryComponentQueryEntity(((Bundle) requestResource).getEntry().get(i));

          restOperationType =
              getRestOperationTypeForBundleEntry(
                  ((Bundle) requestResource).getEntry().get(i), bundleEntryComponentResource);

        } else {
          restOperationType = requestDetailsReader.getRestOperationType();
        }

        if (bundleEntryComponentResource instanceof Bundle) {
          processResponseResourceNestedBundle(
              bundleEntryComponentResource,
              restOperationType,
              resourcesByRestOperationMap,
              nestedResourceQueryEntity);

        } else {
          processSingleResource(
              restOperationType,
              bundleEntryComponentResource,
              resourcesByRestOperationMap,
              nestedResourceQueryEntity);
        }
      }

    } else {
      processSingleResource(
          requestDetailsReader.getRestOperationType(),
          responseResource,
          resourcesByRestOperationMap,
          queryEntity);
    }

    return resourcesByRestOperationMap;
  }

  private AuditEventBuilder.QueryEntity generateBundleEntryComponentQueryEntity(
      Bundle.BundleEntryComponent bundleEntryComponent) {
    return AuditEventBuilder.QueryEntity.builder()
        .completeUrl(
            String.format(
                "%s/%s",
                requestDetailsReader.getFhirServerBase(),
                bundleEntryComponent.getRequest().getUrl()))
        .requestType(RequestTypeEnum.valueOf(bundleEntryComponent.getRequest().getMethod().name()))
        .fhirServerBase(requestDetailsReader.getFhirServerBase())
        .requestPath(
            UrlUtil.determineResourceTypeInResourceUrl(
                fhirContext, bundleEntryComponent.getRequest().getUrl()))
        .parameters(
            UrlUtil.parseQueryString(getQueryStringFromBundleComponent(bundleEntryComponent)))
        .build();
  }

  private void processResponseResourceNestedBundle(
      IBaseResource bundleResource,
      RestOperationTypeEnum restOperationType,
      Map<RestOperationTypeEnum, List<AuditEventBuilder.ResourceContext>>
          resourcesByRestOperationMap,
      AuditEventBuilder.QueryEntity queryEntity) {

    for (Bundle.BundleEntryComponent nestedBundleEntryComponent :
        ((Bundle) bundleResource).getEntry()) {

      IBaseResource entryResource =
          extractResourceFromBundleComponent(null, nestedBundleEntryComponent);

      if (entryResource instanceof Bundle) {

        processResponseResourceNestedBundle(
            entryResource, restOperationType, resourcesByRestOperationMap, queryEntity);

      } else {
        processSingleResource(
            restOperationType, entryResource, resourcesByRestOperationMap, queryEntity);
      }
    }
  }

  /**
   * Generates the source resource for the AuditEvent to be generated. It uses a combination of the
   * request bundle entry component resource, response bundle entry component resource and the
   * `response.location` field of the response bundle entry component to generate the most complete
   * input resource.
   *
   * @param requestBundleEntryComponent the bundle entry component from the request at a specific
   *     index position
   * @param responseBundleEntryComponent the bundle entry component from the response at an index
   *     position corresponding to the {@code requestBundleEntryComponent}
   * @return a FHIR Resource to be used as the input source of the AuditEvent generation
   */
  private @Nullable IBaseResource extractResourceFromBundleComponent(
      Bundle.BundleEntryComponent requestBundleEntryComponent,
      Bundle.BundleEntryComponent responseBundleEntryComponent) {
    IBaseResource resource;
    IBaseResource contentLocationResource = null;

    if (responseBundleEntryComponent.hasResponse()
        && responseBundleEntryComponent.getResponse().getLocation() != null) {
      contentLocationResource =
          FhirUtil.parseResourceOrNull(
              this.fhirContext,
              getResourceFromContentLocation(
                  responseBundleEntryComponent.getResponse().getLocation()));
    }

    if (responseBundleEntryComponent.hasResource()) {
      resource = responseBundleEntryComponent.getResource();

    } else if (requestBundleEntryComponent != null && requestBundleEntryComponent.hasResource()) {
      resource = requestBundleEntryComponent.getResource();
      resource.setId(
          contentLocationResource != null
              ? contentLocationResource.getIdElement()
              : resource.getIdElement());

    } else {
      resource = contentLocationResource;
    }
    return resource;
  }

  private RestOperationTypeEnum getRestOperationTypeForBundleEntry(
      Bundle.BundleEntryComponent requestResourceBundleComponent, IBaseResource resource) {

    RestOperationTypeEnum restOperationType;
    String queryString = getQueryStringFromBundleComponent(requestResourceBundleComponent);

    restOperationType =
        FhirUtil.getRestOperationType(
            requestResourceBundleComponent.getRequest().getMethod().name(),
            resource instanceof Bundle ? null : resource.getIdElement(),
            UrlUtil.parseQueryString(queryString));

    return restOperationType;
  }

  private @Nonnull String getQueryStringFromBundleComponent(
      Bundle.BundleEntryComponent requestResourceBundleComponent) {
    String requestUrl = requestResourceBundleComponent.getRequest().getUrl();
    return !Strings.isNullOrEmpty(requestUrl) && requestUrl.contains("?")
        ? requestUrl.substring(requestUrl.indexOf('?'))
        : "";
  }

  private void processSingleResource(
      RestOperationTypeEnum restOperationType,
      IBaseResource iBaseResource,
      Map<RestOperationTypeEnum, List<AuditEventBuilder.ResourceContext>>
          resourcesByRestOperationMap,
      AuditEventBuilder.QueryEntity queryEntity) {
    if (iBaseResource == null) return;
    resourcesByRestOperationMap
        .computeIfAbsent(restOperationType, key -> new ArrayList<>())
        .add(
            AuditEventBuilder.ResourceContext.builder()
                .resourceEntity(iBaseResource)
                .queryEntity(queryEntity)
                .build());
  }

  // If we get here we capture all audit log details except the compartment owner for non-patient
  // resources. Reason being we don't have the full resource.
  // The content location header carries the minimal information we need to generate the resource
  // identity https://www.hl7.org/fhir/http.html#ops
  // One way to get the patient compartment owner for non-patient resources would be to fetch the
  // actual resource from the database first before creating the AuditEvent.
  private String getResourceFromContentLocation(String responseContentLocation) {
    IdType id = new IdType(responseContentLocation);
    String resourceType = id.getResourceType();
    String resourceId = id.getIdPart();
    return getResourceTemplate(resourceType, resourceId);
  }

  private AuditEventBuilder initBaseAuditEventBuilder(
      RestOperationTypeEnum restOperationType, BalpProfileEnum balpProfile) {

    AuditEventBuilder auditEventBuilder = new AuditEventBuilder(this.periodStartTime);
    auditEventBuilder.restOperationType(restOperationType);
    if (this.decodedJWT != null) {
      auditEventBuilder.agentUserPolicy(
          JwtUtil.getClaimOrDefault(this.decodedJWT, JwtUtil.CLAIM_JWT_ID, ""));
    }
    auditEventBuilder.auditEventAction(balpProfile.getAction());
    auditEventBuilder.agentClientTypeCoding(balpProfile.getAgentClientTypeCoding());
    auditEventBuilder.agentServerTypeCoding(balpProfile.getAgentServerTypeCoding());
    auditEventBuilder.profileUrl(balpProfile.getProfileUrl());
    auditEventBuilder.fhirServerBaseUrl(this.requestDetailsReader.getFhirServerBase());
    auditEventBuilder.requestId(this.requestDetailsReader.getRequestId());
    auditEventBuilder.network(
        AuditEventBuilder.Network.builder()
            .address(this.requestDetailsReader.getServletRequestRemoteAddr())
            .type(BalpConstants.AUDIT_EVENT_AGENT_NETWORK_TYPE_IP_ADDRESS)
            .build());
    auditEventBuilder.agentUserWho(this.agentUserWho);

    if (this.decodedJWT != null) {
      auditEventBuilder.agentClientWho(createAgentClientWhoRef(this.decodedJWT));
    }
    return auditEventBuilder;
  }

  private AuditEvent createAuditEventOperationOutcome(
      RestOperationTypeEnum restOperationType,
      OperationOutcome operationOutcomeResource,
      BalpProfileEnum balpProfile,
      AuditEventBuilder.QueryEntity queryEntity) {
    // We need to capture the context of the operation e.g. Successful DELETE returns
    // OperationOutcome but no info on the affected resource
    String processedResource = getResourceFromContentLocation(this.responseContentLocation);
    IBaseResource resource = FhirUtil.parseResourceOrNull(this.fhirContext, processedResource);

    Set<String> patientIds =
        resource instanceof DomainResource
            ? patientFinder.findPatientIds((DomainResource) resource)
            : Set.of();

    AuditEventBuilder auditEventBuilder =
        getAuditEventBuilder(
            restOperationType, (Resource) resource, balpProfile, patientIds, queryEntity);

    OperationOutcome.OperationOutcomeIssueComponent outcomeIssueComponent =
        operationOutcomeResource.getIssueFirstRep();

    String errorDescription =
        outcomeIssueComponent.getDetails().getText() != null
            ? outcomeIssueComponent.getDetails().getText()
            : outcomeIssueComponent.getDiagnostics();
    auditEventBuilder.outcome(
        AuditEventBuilder.Outcome.builder()
            .code(mapOutcomeErrorCode(outcomeIssueComponent.getSeverity()))
            .description(errorDescription)
            .build());

    return auditEventBuilder.build();
  }

  private AuditEvent createAuditEventEHR(
      RestOperationTypeEnum restOperationType,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners,
      AuditEventBuilder.QueryEntity queryEntity) {

    AuditEventBuilder auditEventBuilder =
        getAuditEventBuilder(
            restOperationType, resource, balpProfile, compartmentOwners, queryEntity);

    return auditEventBuilder.build();
  }

  private AuditEventBuilder getAuditEventBuilder(
      RestOperationTypeEnum restOperationType,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners,
      AuditEventBuilder.QueryEntity queryEntity) {
    AuditEventBuilder auditEventBuilder = initBaseAuditEventBuilder(restOperationType, balpProfile);

    if (!compartmentOwners.isEmpty()) {
      for (String owner : compartmentOwners) {
        auditEventBuilder.addEntityWhat(balpProfile, true, owner);
      }
    }

    auditEventBuilder.addEntityWhat(balpProfile, false, FhirUtil.extractLogicalId(resource));

    if (BalpProfileEnum.BASIC_QUERY.equals(balpProfile)
        || BalpProfileEnum.PATIENT_QUERY.equals(balpProfile)) {

      auditEventBuilder.addQuery(queryEntity);
    }
    return auditEventBuilder;
  }

  private Reference createAgentClientWhoRef(DecodedJWT decodedJWT) {
    String clientId = JwtUtil.getClaimOrDefault(decodedJWT, JwtUtil.CLAIM_IHE_IUA_CLIENT_ID, "");
    clientId =
        Strings.isNullOrEmpty(clientId)
            ? JwtUtil.getClaimOrDefault(decodedJWT, JwtUtil.CLAIM_AZP, "")
            : clientId;
    String issuer = JwtUtil.getClaimOrDefault(decodedJWT, JwtUtil.CLAIM_ISSUER, "");

    return new Reference()
        .setIdentifier(
            new Identifier()
                .setSystem(String.format("%s/oauth-client-id", issuer))
                .setValue(clientId));
  }
}
