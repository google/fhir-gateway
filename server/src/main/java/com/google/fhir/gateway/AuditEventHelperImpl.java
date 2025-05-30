package com.google.fhir.gateway;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.storage.interceptor.balp.BalpConstants;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.fhir.gateway.interfaces.AuditEventHelper;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO unit test this class
public class AuditEventHelperImpl implements AuditEventHelper {

  private static final Logger logger = LoggerFactory.getLogger(AuditEventHelperImpl.class);

  private final PatientFinderImp patientFinder;

  private AuditEventHelperImpl(FhirContext fhirContext) {
    this.patientFinder = PatientFinderImp.getInstance(fhirContext);
  }

  @Override
  public void processAuditEvents(AuditEventHelperInputDto auditEventHelperInputDto) {
    try {
      List<AuditEvent> auditEventList = new ArrayList<>();

      List<IBaseResource> resources =
          extractFhirResources(auditEventHelperInputDto.getResponseStringContent());

      switch (auditEventHelperInputDto.getRequestDetailsReader().getRestOperationType()) {
        case SEARCH_TYPE:
        case SEARCH_SYSTEM:
        case GET_PAGE:
          for (IBaseResource resource : resources) {

            Preconditions.checkState(resources.get(0) instanceof DomainResource);

            Set<String> patientIds = patientFinder.findPatientIds((DomainResource) resource);

            auditEventList =
                createAuditEventCore(
                    auditEventHelperInputDto,
                    resource,
                    patientIds,
                    BalpProfileEnum.PATIENT_QUERY,
                    BalpProfileEnum.BASIC_QUERY);
          }
          break;

        case READ:
        case VREAD:
          if (!resources.isEmpty()) {

            Preconditions.checkState(resources.get(0) instanceof DomainResource);

            DomainResource resource = (DomainResource) resources.get(0);

            Set<String> patientIds = patientFinder.findPatientIds(resource);

            auditEventList =
                createAuditEventCore(
                    auditEventHelperInputDto,
                    resource,
                    patientIds,
                    BalpProfileEnum.PATIENT_READ,
                    BalpProfileEnum.BASIC_READ);
          }

          break;

        case CREATE: // TODO handle case where the full resource is not returned (only ID)
          if (!resources.isEmpty()) {

            Preconditions.checkState(resources.get(0) instanceof DomainResource);

            DomainResource resource = (DomainResource) resources.get(0);

            Set<String> patientIds = patientFinder.findPatientIds(resource);

            auditEventList =
                createAuditEventCore(
                    auditEventHelperInputDto,
                    resource,
                    patientIds,
                    BalpProfileEnum.PATIENT_CREATE,
                    BalpProfileEnum.BASIC_CREATE);
          }
          break;

        case UPDATE:
          if (!resources.isEmpty()) {

            Preconditions.checkState(resources.get(0) instanceof DomainResource);

            DomainResource resource = (DomainResource) resources.get(0);

            Set<String> patientIds = patientFinder.findPatientIds(resource);

            auditEventList =
                createAuditEventCore(
                    auditEventHelperInputDto,
                    resource,
                    patientIds,
                    BalpProfileEnum.PATIENT_UPDATE,
                    BalpProfileEnum.PATIENT_UPDATE);
          }
          break;

        case DELETE:

          // NOTE: The success of processing of DELETE is heavily dependent on server validation
          // policy e.g. If you have a permission list that references the resource being deleted,
          // it
          // breaks the delete operation. Like wise if you reference the resource in AuditEvent such
          // as logging when you first created or updated it the operation breaks. In case of such
          // an
          // error, AuditEvent.outcome and AuditEvent.outcomeDesc are populated accordingly

          // Also note, this Implementation only fully captures that a specific resource was deleted
          // and by whom but not the compartment owner.
          // With no access to the actual deleted resource we can't get the compartment owner unless
          // the resource itself is a Patient. A crude way to get the owner would be to fetch the
          // actual resource from the database first before creating the AuditEvent.

          // This implementation skips logging Deletes if the operation is conditional (thus no
          // Resource ID present)

          if (auditEventHelperInputDto.getRequestDetailsReader().getId() != null) {

            String transientDeleteResourceRaw =
                String.format(
                    "{ \"resourceType\": \"%s\", \"id\": \"%s\"}",
                    auditEventHelperInputDto.getRequestDetailsReader().getResourceName(),
                    auditEventHelperInputDto.getRequestDetailsReader().getId().getIdPart());
            IBaseResource transientDeleteResource =
                FhirContext.forR4Cached().newJsonParser().parseResource(transientDeleteResourceRaw);

            Preconditions.checkState(transientDeleteResource instanceof DomainResource);

            boolean serverError =
                !resources.isEmpty() && resources.get(0) instanceof OperationOutcome;
            DomainResource resource =
                serverError
                    ? (DomainResource) resources.get(0)
                    : (DomainResource) transientDeleteResource;

            Set<String> patientIds = patientFinder.findPatientIds(resource);

            auditEventList =
                createAuditEventCore(
                    auditEventHelperInputDto,
                    resource,
                    patientIds,
                    BalpProfileEnum.PATIENT_DELETE,
                    BalpProfileEnum.BASIC_DELETE);
          }
          break;

        default:
          break;
      }

      // TODO Investigate bulk saving (batch processing) instead to improve performance. We'll
      // probably need a mechanism for chunking e.g. 100, 200, or 500 batch
      for (AuditEvent auditEvent : auditEventList) {
        auditEvent.getPeriod().setEnd(new Date());
        auditEventHelperInputDto.httpFhirClient.postResource(auditEvent);
      }
    } catch (IllegalStateException | IOException exception) {
      logger.error(exception.getMessage(), exception);
    }
  }

  private List<AuditEvent> createAuditEventCore(
      AuditEventHelperInputDto auditEventHelperInputDto,
      IBaseResource resource,
      Set<String> patientIds,
      BalpProfileEnum patientProfile,
      BalpProfileEnum basicProfile) {
    List<AuditEvent> auditEventList = new ArrayList<>();

    if (!patientIds.isEmpty()) {
      auditEventList.add(
          createAuditEvent(
              auditEventHelperInputDto, (DomainResource) resource, patientProfile, patientIds));
    } else {
      if (resource instanceof DomainResource)
        auditEventList.add(
            createAuditEvent(
                auditEventHelperInputDto, (DomainResource) resource, basicProfile, null));
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

  private List<IBaseResource> extractFhirResources(String serverContentResponse) {
    List<IBaseResource> resourceList = new ArrayList<>();

    IBaseResource responseResource =
        FhirContext.forR4Cached().newJsonParser().parseResource(serverContentResponse);

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

  private AuditEventBuilder initBaseAuditEventBuilder(
      AuditEventHelperInputDto auditEventHelperInputDto, BalpProfileEnum balpProfile) {

    AuditEventBuilder auditEventBuilder =
        new AuditEventBuilder(auditEventHelperInputDto.getPeriodStartTime());
    auditEventBuilder.restOperationType(
        auditEventHelperInputDto.getRequestDetailsReader().getRestOperationType());
    auditEventBuilder.agentUserPolicy(
        JwtUtil.getClaimOrDefault(
            auditEventHelperInputDto.getDecodedJWT(), JwtUtil.CLAIM_JWT_ID, ""));
    auditEventBuilder.auditEventAction(balpProfile.getAction());
    auditEventBuilder.agentClientTypeCoding(balpProfile.getAgentClientTypeCoding());
    auditEventBuilder.agentServerTypeCoding(balpProfile.getAgentServerTypeCoding());
    auditEventBuilder.profileUrl(balpProfile.getProfileUrl());
    auditEventBuilder.fhirServerBaseUrl(
        auditEventHelperInputDto.getRequestDetailsReader().getFhirServerBase());
    auditEventBuilder.requestId(auditEventHelperInputDto.getRequestDetailsReader().getRequestId());
    auditEventBuilder.network(
        AuditEventBuilder.Network.builder()
            .address(
                auditEventHelperInputDto.getRequestDetailsReader().getServletRequestRemoteAddr())
            .type(BalpConstants.AUDIT_EVENT_AGENT_NETWORK_TYPE_IP_ADDRESS)
            .build());
    auditEventBuilder.agentUserWho(auditEventHelperInputDto.getAgentUserWho());
    auditEventBuilder.agentClientWho(
        createAgentClientWhoRef(auditEventHelperInputDto.getDecodedJWT()));

    return auditEventBuilder;
  }

  private AuditEvent createAuditEvent(
      AuditEventHelperInputDto auditEventHelperInputDto,
      Resource resource,
      BalpProfileEnum balpProfile,
      Set<String> compartmentOwners) {

    AuditEventBuilder auditEventBuilder =
        initBaseAuditEventBuilder(auditEventHelperInputDto, balpProfile);

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
        auditEventBuilder.addEntityWhat(balpProfile, false, FhirUtil.extractLogicalId(resource));
      }

      if (BalpProfileEnum.BASIC_QUERY.equals(balpProfile)
          || BalpProfileEnum.PATIENT_QUERY.equals(balpProfile)) {
        auditEventBuilder.addQuery(auditEventHelperInputDto.getRequestDetailsReader());
      }
    }

    return auditEventBuilder.build();
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

  public static AuditEventHelper createNewInstance(FhirContext fhirContext) {
    return new AuditEventHelperImpl(fhirContext);
  }

  @Getter
  @Builder
  public static class AuditEventHelperInputDto {
    private RequestDetailsReader requestDetailsReader;
    private String responseStringContent;
    private Reference agentUserWho;
    private DecodedJWT decodedJWT;
    private Date periodStartTime;
    private HttpFhirClient httpFhirClient;
  }
}
