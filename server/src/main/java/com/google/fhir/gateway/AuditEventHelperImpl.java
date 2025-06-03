package com.google.fhir.gateway;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.storage.interceptor.balp.BalpConstants;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import ca.uhn.fhir.util.UrlUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.fhir.gateway.interfaces.AuditEventHelper;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.hl7.fhir.r4.model.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO unit test this class

/**
 * This class is meant to be immutable with a new instance created for to process each individual
 * request.
 */
public class AuditEventHelperImpl implements AuditEventHelper {

  private static final Logger logger = LoggerFactory.getLogger(AuditEventHelperImpl.class);

  private final PatientFinderImp patientFinder;
  private final RequestDetailsReader requestDetailsReader;
  private final String responseContent;
  private final String responseContentLocation;
  private final Reference agentUserWho;
  private final DecodedJWT decodedJWT;
  private final Date periodStartTime;
  private final HttpFhirClient httpFhirClient;
  private final FhirContext fhirContext;

  public AuditEventHelperImpl(
      RequestDetailsReader requestDetailsReader,
      String responseContent,
      String responseContentLocation,
      Reference agentUserWho,
      DecodedJWT decodedJWT,
      Date periodStartTime,
      HttpFhirClient httpFhirClient,
      FhirContext fhirContext) {
    this.patientFinder = PatientFinderImp.getInstance(fhirContext);
    this.requestDetailsReader = requestDetailsReader;
    this.responseContent = responseContent;
    this.responseContentLocation = responseContentLocation;
    this.agentUserWho = agentUserWho;
    this.decodedJWT = decodedJWT;
    this.periodStartTime = periodStartTime;
    this.httpFhirClient = httpFhirClient;
    this.fhirContext = fhirContext;
  }

  // TODO handle the case for Bundle operations
  @Override
  public void processAuditEvents() {
    try {
      List<AuditEvent> auditEventList = new ArrayList<>();

      Map<RestOperationTypeEnum, List<IBaseResource>> resourcesMap =
          extractFhirResourcesByRestOperationType(responseContent);

      resourcesMap.forEach(
          (restOperationType, resources) -> {
            auditEventList.addAll(getAuditEvents(restOperationType, resources));
          });

      // TODO Investigate bulk saving (batch processing) instead to improve performance. We'll
      // probably need a mechanism for chunking e.g. 100, 200, or 500 batch
      for (AuditEvent auditEvent : auditEventList) {
        if (auditEvent != null) {
          auditEvent.getPeriod().setEnd(new Date());
          this.httpFhirClient.postResource(auditEvent);
        }
      }
    } catch (IOException exception) {
      logger.error(exception.getMessage(), exception);
    }
  }

  private @NotNull List<AuditEvent> getAuditEvents(
      RestOperationTypeEnum restOperationType, List<IBaseResource> resources) {
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
                BalpProfileEnum.PATIENT_UPDATE);
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

  private String getResourceTemplate(String resourceName, String resourceId) {
    return String.format("{ \"resourceType\": \"%s\", \"id\": \"%s\"}", resourceName, resourceId);
  }

  private List<AuditEvent> createAuditEventCore(
      RestOperationTypeEnum restOperationType,
      List<IBaseResource> resources,
      BalpProfileEnum patientProfile,
      BalpProfileEnum basicProfile) {

    List<AuditEvent> auditEventList = new ArrayList<>();

    for (IBaseResource iBaseResource : resources) {
      try {

        if (iBaseResource == null) continue;

        Preconditions.checkState(iBaseResource instanceof DomainResource);

        DomainResource resource = (DomainResource) iBaseResource;

        Set<String> patientIds = patientFinder.findPatientIds(resource);

        if (!patientIds.isEmpty()) {
          auditEventList.add(
              createAuditEvent(restOperationType, resource, patientProfile, patientIds));
        } else {
          auditEventList.add(createAuditEvent(restOperationType, resource, basicProfile, null));
        }
      } catch (IllegalStateException exception) {
        logger.error(exception.getMessage(), exception);
      }
    }

    return auditEventList;
  }

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

  private Map<RestOperationTypeEnum, List<IBaseResource>> extractFhirResourcesByRestOperationType(
      String _serverContentResponse) {
    Map<RestOperationTypeEnum, List<IBaseResource>> resourcesByRestOperationMap = new HashMap<>();

    // This condition handles operations with no response body returned e.g. POST/PUT requests with
    // Prefer: return=minimal HTTP header
    String serverContentResponse =
        _serverContentResponse.isEmpty()
            ? getResourceFromContentLocation(this.responseContentLocation)
            : _serverContentResponse;

    IBaseResource responseResource =
        this.fhirContext.newJsonParser().parseResource(serverContentResponse);

    boolean isPostBundle =
        requestDetailsReader.getRequestType() == RequestTypeEnum.POST
            && requestDetailsReader.getResourceName() == null;
    String requestResourceString =
        isPostBundle
            ? new String(requestDetailsReader.loadRequestContents(), StandardCharsets.UTF_8)
            : null;
    Bundle requestResourceBundle =
        requestResourceString != null
            ? (Bundle) fhirContext.newJsonParser().parseResource(requestResourceString)
            : null;

    if (responseResource
        instanceof Bundle) { // TODO implement processing of Nested Bundles returned as entries

      List<Bundle.BundleEntryComponent> responseBundleEntryComponents =
          ((Bundle) responseResource).getEntry();
      for (int i = 0; i < responseBundleEntryComponents.size(); i++) {

        IBaseResource resource = null;

        if (responseBundleEntryComponents.get(i).hasResource()) {

          resource = responseBundleEntryComponents.get(i).getResource();
        } else if (responseBundleEntryComponents.get(i).hasResponse()) {

          resource =
              fhirContext
                  .newJsonParser()
                  .parseResource(
                      getResourceFromContentLocation(
                          responseBundleEntryComponents.get(i).getResponse().getLocation()));
        }

        if (resource == null) continue;

        RestOperationTypeEnum restOperationType = null;
        if (isPostBundle) {

          if (requestResourceBundle != null) {

            String requestUrl = requestResourceBundle.getEntry().get(i).getRequest().getUrl();
            String queryString =
                !Strings.isNullOrEmpty(requestUrl) && requestUrl.contains("?")
                    ? requestUrl.substring(requestUrl.indexOf('?'))
                    : "";

            restOperationType =
                FhirUtil.getRestOperationType(
                    requestResourceBundle.getEntry().get(i).getRequest().getMethod().name(),
                    resource instanceof Bundle ? null : resource.getIdElement(),
                    UrlUtil.parseQueryString(queryString));
          }
        } else {
          restOperationType = requestDetailsReader.getRestOperationType();
        }

        resourcesByRestOperationMap
            .computeIfAbsent(restOperationType, key -> new ArrayList<>())
            .add(resource);
      }

    } else {
      resourcesByRestOperationMap.put(
          requestDetailsReader.getRestOperationType(), List.of(responseResource));
    }

    return resourcesByRestOperationMap;
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
    auditEventBuilder.agentUserPolicy(
        JwtUtil.getClaimOrDefault(this.decodedJWT, JwtUtil.CLAIM_JWT_ID, ""));
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
    auditEventBuilder.agentClientWho(createAgentClientWhoRef(this.decodedJWT));

    return auditEventBuilder;
  }

  private AuditEvent createAuditEvent(
      RestOperationTypeEnum restOperationType,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners) {

    if (resource instanceof OperationOutcome) {

      return createAuditEventOperationOutcome(
          restOperationType, (OperationOutcome) resource, balpProfile);

    } else {

      return createAuditEventEHR(restOperationType, resource, balpProfile, compartmentOwners);
    }
  }

  private AuditEvent createAuditEventOperationOutcome(
      RestOperationTypeEnum restOperationType,
      OperationOutcome operationOutcomeResource,
      BalpProfileEnum balpProfile) {

    try {
      // We need to capture the context of the operation e.g. Successful DELETE returns
      // OperationOutcome but no info on the affected resource
      String processedResource = getResourceFromContentLocation(this.responseContentLocation);
      IBaseResource resource = fhirContext.newJsonParser().parseResource(processedResource);

      Set<String> patientIds =
          resource instanceof DomainResource
              ? patientFinder.findPatientIds((DomainResource) resource)
              : Set.of();

      AuditEventBuilder auditEventBuilder =
          getAuditEventBuilder(restOperationType, (Resource) resource, balpProfile, patientIds);

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

    } catch (DataFormatException dataFormatException) {
      logger.error(dataFormatException.getMessage(), dataFormatException);
    }
    return null;
  }

  private AuditEvent createAuditEventEHR(
      RestOperationTypeEnum restOperationType,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners) {

    AuditEventBuilder auditEventBuilder =
        getAuditEventBuilder(restOperationType, resource, balpProfile, compartmentOwners);

    return auditEventBuilder.build();
  }

  private @NotNull AuditEventBuilder getAuditEventBuilder(
      RestOperationTypeEnum restOperationType,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners) {
    AuditEventBuilder auditEventBuilder = initBaseAuditEventBuilder(restOperationType, balpProfile);

    if (compartmentOwners != null && !compartmentOwners.isEmpty()) {
      for (String owner : compartmentOwners) {
        auditEventBuilder.addEntityWhat(balpProfile, true, owner);
      }
    }

    if (!ResourceType.Patient.equals(resource.getResourceType())) {
      auditEventBuilder.addEntityWhat(balpProfile, false, FhirUtil.extractLogicalId(resource));
    }

    if (BalpProfileEnum.BASIC_QUERY.equals(balpProfile)
        || BalpProfileEnum.PATIENT_QUERY.equals(balpProfile)) {
      auditEventBuilder.addQuery(this.requestDetailsReader);
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
