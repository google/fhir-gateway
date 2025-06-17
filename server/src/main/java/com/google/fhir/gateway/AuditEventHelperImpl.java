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
import ca.uhn.fhir.storage.interceptor.balp.BalpConstants;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Strings;
import com.google.fhir.gateway.interfaces.AuditEventHelper;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    this.responseContent = responseContent;
    this.responseContentLocation = responseContentLocation;
    this.agentUserWho = agentUserWho;
    this.decodedJWT = decodedJWT;
    this.periodStartTime = periodStartTime;
    this.httpFhirClient = httpFhirClient;
    this.fhirContext = fhirContext;
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

  // TODO handle the case for Bundle operations
  @Override
  public void processAuditEvents() {

    List<AuditEvent> auditEventList = new ArrayList<>();

    List<IBaseResource> resources = extractFhirResources(responseContent);

    switch (this.requestDetailsReader.getRestOperationType()) {
      case SEARCH_TYPE:
      case SEARCH_SYSTEM:
      case GET_PAGE:
        auditEventList =
            createAuditEventCore(
                resources, BalpProfileEnum.PATIENT_QUERY, BalpProfileEnum.BASIC_QUERY);
        break;

      case READ:
      case VREAD:
        auditEventList =
            createAuditEventCore(
                resources, BalpProfileEnum.PATIENT_READ, BalpProfileEnum.BASIC_READ);
        break;

      case CREATE:
        auditEventList =
            createAuditEventCore(
                resources, BalpProfileEnum.PATIENT_CREATE, BalpProfileEnum.BASIC_CREATE);
        break;

      case UPDATE:
        auditEventList =
            createAuditEventCore(
                resources, BalpProfileEnum.PATIENT_UPDATE, BalpProfileEnum.BASIC_UPDATE);
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
                resources, BalpProfileEnum.PATIENT_DELETE, BalpProfileEnum.BASIC_DELETE);
        break;

      default:
        break;
    }

    // TODO Investigate bulk saving (batch processing) instead to improve performance. We'll
    // probably need a mechanism for chunking e.g. 100, 200, or 500 batch
    for (AuditEvent auditEvent : auditEventList) {
      auditEvent.getPeriod().setEnd(new Date());
      try {
        this.httpFhirClient.postResource(auditEvent);
      } catch (IOException exception) {
        ExceptionUtil.throwRuntimeExceptionAndLog(logger, exception.getMessage(), exception);
      }
    }
  }

  private String getResourceTemplate(String resourceType, String resourceId) {
    return String.format("{ \"resourceType\": \"%s\", \"id\": \"%s\"}", resourceType, resourceId);
  }

  private List<AuditEvent> createAuditEventCore(
      List<IBaseResource> resources, BalpProfileEnum patientProfile, BalpProfileEnum basicProfile) {
    List<AuditEvent> auditEventList = new ArrayList<>();

    for (IBaseResource iBaseResource : resources) {

      if (iBaseResource instanceof DomainResource) {

        DomainResource resource = (DomainResource) iBaseResource;
        Set<String> patientIds = patientFinder.findPatientIds(resource);

        if (!patientIds.isEmpty()) {
          if (resource instanceof OperationOutcome) {
            auditEventList.add(
                createAuditEventOperationOutcome((OperationOutcome) resource, patientProfile));
          } else {
            auditEventList.add(createAuditEventEHR(resource, patientProfile, patientIds));
          }

        } else {
          if (resource instanceof OperationOutcome) {
            auditEventList.add(
                createAuditEventOperationOutcome((OperationOutcome) resource, basicProfile));
          } else {
            auditEventList.add(createAuditEventEHR(resource, basicProfile, Set.of()));
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

  private List<IBaseResource> extractFhirResources(String serverContentResponse) {
    List<IBaseResource> resourceList = new ArrayList<>();

    // This condition handles operations with no response body returned e.g. POST/PUT requests with
    // Prefer: return=minimal HTTP header
    String resolvedServerContentResponse =
        serverContentResponse.isEmpty()
            ? getResourceFromContentLocation(this.responseContentLocation)
            : serverContentResponse;

    IBaseResource responseResource =
        this.fhirContext.newJsonParser().parseResource(resolvedServerContentResponse);

    if (responseResource instanceof Bundle) {

      resourceList =
          ((Bundle) responseResource)
              .getEntry().stream()
                  .map(
                      it -> {
                        IBaseResource resource = null;

                        if (it.hasResource()) {

                          resource = it.getResource();
                        } else if (it.hasResponse()) {

                          resource =
                              fhirContext
                                  .newJsonParser()
                                  .parseResource(
                                      getResourceFromContentLocation(
                                          it.getResponse().getLocation()));
                        }

                        return resource;
                      })
                  .collect(Collectors.toList());

    } else {
      resourceList.add(responseResource);
    }

    return resourceList;
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

  private AuditEventBuilder initBaseAuditEventBuilder(BalpProfileEnum balpProfile) {
    AuditEventBuilder auditEventBuilder = new AuditEventBuilder(this.periodStartTime);
    auditEventBuilder.restOperationType(this.requestDetailsReader.getRestOperationType());
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
      OperationOutcome operationOutcomeResource, BalpProfileEnum balpProfile) {
    // We need to capture the context of the operation e.g. Successful DELETE returns
    // OperationOutcome but no info on the affected resource
    String processedResource = getResourceFromContentLocation(this.responseContentLocation);
    IBaseResource resource = fhirContext.newJsonParser().parseResource(processedResource);

    Set<String> patientIds =
        resource instanceof DomainResource
            ? patientFinder.findPatientIds((DomainResource) resource)
            : Set.of();

    AuditEventBuilder auditEventBuilder =
        getAuditEventBuilder((Resource) resource, balpProfile, patientIds);

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
      Resource resource, BalpProfileEnum balpProfile, Set<String> compartmentOwners) {
    AuditEventBuilder auditEventBuilder =
        getAuditEventBuilder(resource, balpProfile, compartmentOwners);

    return auditEventBuilder.build();
  }

  private AuditEventBuilder getAuditEventBuilder(
      Resource resource, BalpProfileEnum balpProfile, Set<String> compartmentOwners) {
    AuditEventBuilder auditEventBuilder = initBaseAuditEventBuilder(balpProfile);

    if (!compartmentOwners.isEmpty()) {
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
