package com.google.fhir.gateway;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.storage.interceptor.balp.BalpConstants;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import ca.uhn.fhir.util.FhirTerser;
import com.google.fhir.gateway.interfaces.AuditEventHelper;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import jakarta.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.http.util.TextUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.CompartmentType;

public class AuditEventHelperImpl implements AuditEventHelper {

  private final IGenericClient iGenericClient;
  private final PatientFinderImp patientFinder;
  private final Date startTime;

  private AuditEventHelperImpl(FhirContext fhirContext, String baseUrl) {
    this.iGenericClient = fhirContext.newRestfulGenericClient(baseUrl);
    this.patientFinder = PatientFinderImp.getInstance(fhirContext);
    this.startTime = new Date();
  }

  @Override
  public void processAuditEvents(
      RequestDetailsReader requestDetailsReader,
      String serverContentResponse,
      Reference agentUserWho) {

    List<AuditEvent> auditEventList = new ArrayList<>();

    List<IBaseResource> resources = extractFhirResources(serverContentResponse);

    switch (requestDetailsReader.getRestOperationType()) {
      case SEARCH_TYPE:
      case SEARCH_SYSTEM:
      case GET_PAGE:
        for (IBaseResource resource : resources) {
          Set<String> patientIds =
              resource instanceof DomainResource
                  ? getPatientCompartmentOwners((DomainResource) resource)
                  : Set.of();

          if (!patientIds.isEmpty()) {
            auditEventList.add(
                createAuditEventSearch(
                    requestDetailsReader,
                    (DomainResource) resource,
                    BalpProfileEnum.PATIENT_QUERY,
                    agentUserWho,
                    patientIds));
          } else {
            // TODO investigate if we need to record a resource type that is NOT a DomainResource
            if (resource instanceof DomainResource)
              auditEventList.add(
                  createAuditEventSearch(
                      requestDetailsReader,
                      (DomainResource) resource,
                      BalpProfileEnum.BASIC_QUERY,
                      agentUserWho,
                      null));
          }
        }
        break;

      case READ:
      case VREAD:
        if (!resources.isEmpty()
            && resources.get(0)
                instanceof DomainResource) { // Skip non DomainResources, see TODO above

          DomainResource resource = (DomainResource) resources.get(0);

          Set<String> patientIds = getPatientCompartmentOwners(resource);

          if (!patientIds.isEmpty()) {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.PATIENT_READ,
                    agentUserWho,
                    patientIds));
          } else {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.BASIC_READ,
                    agentUserWho,
                    null));
          }
        }

        break;

      case CREATE:
        if (!resources.isEmpty() && resources.get(0) instanceof DomainResource) {

          DomainResource resource = (DomainResource) resources.get(0);

          Set<String> patientIds = getPatientCompartmentOwners(resource);

          if (!patientIds.isEmpty()) {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.PATIENT_CREATE,
                    agentUserWho,
                    patientIds));
          } else {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.BASIC_CREATE,
                    agentUserWho,
                    null));
          }
        }
        break;

      case UPDATE:
        if (!resources.isEmpty() && resources.get(0) instanceof DomainResource) {

          DomainResource resource = (DomainResource) resources.get(0);

          Set<String> patientIds = getPatientCompartmentOwners(resource);

          if (!patientIds.isEmpty()) {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.PATIENT_UPDATE,
                    agentUserWho,
                    patientIds));
          } else {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.BASIC_UPDATE,
                    agentUserWho,
                    null));
          }
        }
        break;

      case DELETE:

        // NOTE: The success of processing of DELETE is heavily dependent on server validation
        // policy e.g. If you have a permission list that references the resource being deleted, it
        // breaks the delete operation. Like wise if you reference the resource in AuditEvent such
        // as logging when you first created or updated it the operation breaks. In case of such an
        // error, AuditEvent.outcome and AuditEvent.outcomeDesc are populated accordingly

        // Also note, this Implementation only fully captures that a specific resource was deleted
        // and by whom but not the compartment owner.
        // With no access to the actual deleted resource we can't get the compartment owner unless
        // the resource itself is a Patient. A crude way to get the owner would be to fetch the
        // actual resource from the database first before creating the AuditEvent.
        // TODO investigate a better to do this
        String transientDeleteResourceRaw =
            String.format(
                "{ \"resourceType\": \"%s\", \"id\": \"%s\"}",
                requestDetailsReader.getResourceName(), requestDetailsReader.getId().getIdPart());
        IBaseResource transientDeleteResource =
            iGenericClient
                .getFhirContext()
                .newJsonParser()
                .parseResource(transientDeleteResourceRaw);

        if (transientDeleteResource instanceof DomainResource) {
          boolean serverError =
              !resources.isEmpty() && resources.get(0) instanceof OperationOutcome;
          DomainResource resource =
              serverError
                  ? (DomainResource) resources.get(0)
                  : (DomainResource) transientDeleteResource;

          Set<String> patientIds = getPatientCompartmentOwners(resource);

          if (!patientIds.isEmpty()) {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.PATIENT_DELETE,
                    agentUserWho,
                    patientIds));
          } else {
            auditEventList.add(
                createAuditEventCRUD(
                    requestDetailsReader,
                    resource,
                    BalpProfileEnum.BASIC_DELETE,
                    agentUserWho,
                    null));
          }
        }

        break;

      default:
        break;
    }

    // TODO Investigate bulk saving (batch processing) instead to improve performance. We'll
    // probably need a mechanism for chunking e.g. 100, 200, or 500 batch
    for (AuditEvent auditEvent : auditEventList) {
      auditEvent.getPeriod().setEnd(new Date());
      this.iGenericClient.create().resource(auditEvent).encodedJson().execute();
    }
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

  private List<IBaseResource> extractFhirResources(String serverContentResponse) {
    List<IBaseResource> resourceList = new ArrayList<>();

    IBaseResource responseResource =
        this.iGenericClient.getFhirContext().newJsonParser().parseResource(serverContentResponse);

    if (responseResource instanceof Bundle) {

      resourceList =
          ((Bundle) responseResource)
              .getEntry().stream()
                  .map(Bundle.BundleEntryComponent::getResource)
                  .collect(Collectors.toList());

    } else {
      resourceList.add(responseResource);
    }

    return resourceList;
  }

  private Set<String> getCompartmentOwners(
      Resource resource, CompartmentType compartmentType, FhirContext fhirContext) {

    Set<String> compartmentOwnerIds = new TreeSet<>();

    RuntimeResourceDefinition resourceDefinition = fhirContext.getResourceDefinition(resource);
    if (resourceDefinition.getName().equals(compartmentType.getDisplay())) {
      compartmentOwnerIds.add(extractLogicalId(resource));
    } else {
      List<RuntimeSearchParam> compartmentSearchParameters =
          resourceDefinition.getSearchParamsForCompartmentName(compartmentType.getDisplay());
      if (!compartmentSearchParameters.isEmpty()) {
        FhirTerser terser = fhirContext.newTerser();
        terser
            .getCompartmentOwnersForResource(compartmentType.getDisplay(), resource, Set.of())
            .stream()
            .map(IIdType::getValue)
            .forEach(compartmentOwnerIds::add);
      }
    }
    return compartmentOwnerIds;
  }

  private String extractLogicalId(Resource resource) {
    return resource.getResourceType() + "/" + resource.getIdElement().getIdPart();
  }

  private Set<String> getPatientCompartmentOwners(DomainResource resource) {
    return getCompartmentOwners(resource, CompartmentType.PATIENT, iGenericClient.getFhirContext());
  }

  private AuditEventBuilder initBaseAuditEventBuilder(
      RequestDetailsReader requestDetailsReader,
      BalpProfileEnum balpProfile,
      Reference agentUserWho) {

    AuditEventBuilder auditEventBuilder = new AuditEventBuilder(this.startTime);
    auditEventBuilder.restOperationType(requestDetailsReader.getRestOperationType());
    auditEventBuilder.agentUserPolicy(
        JwtUtil.getClaimFromRequestDetails(requestDetailsReader, JwtUtil.CLAIM_JWT_ID));
    auditEventBuilder.auditEventAction(balpProfile.getAction());
    auditEventBuilder.agentClientTypeCoding(balpProfile.getAgentClientTypeCoding());
    auditEventBuilder.agentServerTypeCoding(balpProfile.getAgentServerTypeCoding());
    auditEventBuilder.profileUrl(balpProfile.getProfileUrl());
    auditEventBuilder.fhirServerBaseUrl(requestDetailsReader.getFhirServerBase());
    auditEventBuilder.requestId(requestDetailsReader.getRequestId());
    auditEventBuilder.network(
        AuditEventBuilder.Network.builder()
            .address(requestDetailsReader.getServletRequestRemoteAddr())
            .type(BalpConstants.AUDIT_EVENT_AGENT_NETWORK_TYPE_IP_ADDRESS)
            .build());
    auditEventBuilder.agentUserWho(agentUserWho);
    auditEventBuilder.agentClientWho(createAgentClientWhoRef(requestDetailsReader));

    return auditEventBuilder;
  }

  @Nonnull
  private AuditEvent createAuditEventSearch(
      RequestDetailsReader requestDetailsReader,
      Resource resource,
      BalpProfileEnum balpProfile,
      Reference agentUserWho,
      Set<String> compartmentOwners) {

    AuditEventBuilder auditEventBuilder =
        initBaseAuditEventBuilder(requestDetailsReader, balpProfile, agentUserWho);

    if (resource instanceof OperationOutcome) {

      OperationOutcome.OperationOutcomeIssueComponent outcomeIssueComponent =
          ((OperationOutcome) resource).getIssueFirstRep();

      String errorDescription =
          outcomeIssueComponent.getDetails().getText() != null
              ? outcomeIssueComponent.getDetails().getText()
              : outcomeIssueComponent.getDiagnostics();
      auditEventBuilder.outcome(
          AuditEventBuilder.Outcome.builder()
              .code(mapOutcomeErrorCode(outcomeIssueComponent.getSeverity()))
              .description(errorDescription)
              .build());

    } else {

      if (compartmentOwners != null && !compartmentOwners.isEmpty()) {
        for (String owner : compartmentOwners) {
          auditEventBuilder.addEntityWhat(balpProfile, true, owner);
        }
      }

      if (!ResourceType.Patient.equals(resource.getResourceType())) {
        auditEventBuilder.addEntityWhat(balpProfile, false, extractLogicalId(resource));
      }

      auditEventBuilder.addQuery(requestDetailsReader);
    }

    return auditEventBuilder.build();
  }

  private AuditEvent createAuditEventCRUD(
      RequestDetailsReader requestDetailsReader,
      Resource resource,
      BalpProfileEnum balpProfile,
      Reference agentUserWho,
      Set<String> compartmentOwners) {

    AuditEventBuilder auditEventBuilder =
        initBaseAuditEventBuilder(requestDetailsReader, balpProfile, agentUserWho);
    if (resource instanceof OperationOutcome) {

      OperationOutcome.OperationOutcomeIssueComponent outcomeIssueComponent =
          ((OperationOutcome) resource).getIssueFirstRep();

      String errorDescription =
          outcomeIssueComponent.getDetails().getText() != null
              ? outcomeIssueComponent.getDetails().getText()
              : outcomeIssueComponent.getDiagnostics();
      auditEventBuilder.outcome(
          AuditEventBuilder.Outcome.builder()
              .code(mapOutcomeErrorCode(outcomeIssueComponent.getSeverity()))
              .description(errorDescription)
              .build());

    } else {
      if (compartmentOwners != null && !compartmentOwners.isEmpty()) {
        for (String owner : compartmentOwners) {
          auditEventBuilder.addEntityWhat(balpProfile, true, owner);
        }
      }

      if (!ResourceType.Patient.equals(resource.getResourceType())) {
        auditEventBuilder.addEntityWhat(balpProfile, false, extractLogicalId(resource));
      }
    }

    return auditEventBuilder.build();
  }

  private Reference createAgentClientWhoRef(RequestDetailsReader request) {
    String clientId = JwtUtil.getClaimFromRequestDetails(request, JwtUtil.CLAIM_IHE_IUA_CLIENT_ID);
    clientId =
        TextUtils.isEmpty(clientId)
            ? JwtUtil.getClaimFromRequestDetails(request, JwtUtil.CLAIM_AZP)
            : "";
    String issuer = JwtUtil.getClaimFromRequestDetails(request, JwtUtil.CLAIM_ISSUER);

    return new Reference()
        .setIdentifier(
            new Identifier()
                .setSystem(String.format("%s/oauth-client-id", issuer))
                .setValue(clientId));
  }

  public static AuditEventHelper createNewInstance(FhirContext fhirContext, String baseUrl) {
    return new AuditEventHelperImpl(fhirContext, baseUrl);
  }
}
