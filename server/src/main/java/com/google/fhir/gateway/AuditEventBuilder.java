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

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import ca.uhn.fhir.util.UrlUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.codesystems.AuditEntityType;
import org.hl7.fhir.r4.model.codesystems.AuditEventType;
import org.hl7.fhir.r4.model.codesystems.ObjectRole;
import org.hl7.fhir.r4.model.codesystems.RestfulInteraction;
import org.hl7.fhir.r4.model.codesystems.V3ParticipationType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class AuditEventBuilder {

  public static final String CS_BALP_BASIC_AUDIT_ENTITY_TYPE =
      "https://profiles.ihe.net/ITI/BALP/CodeSystem/BasicAuditEntityType";
  public static final String CS_BALP_BASIC_AUDIT_ENTITY_TYPE_CODE = "XrequestId";

  // TODO investigate whether we need to create a CodeSystem Enum
  public static final String CS_INFO_GATEWAY_DELETED = "http://fhir-info-gateway/deleted";

  private Reference agentUserWho;
  private String profileUrl;
  private AuditEvent.AuditEventAction auditEventAction;
  private RestOperationTypeEnum restOperationType;
  private String fhirServerBaseUrl;
  private String agentUserPolicy;
  private String requestId;
  private Coding agentServerTypeCoding;
  private Coding agentClientTypeCoding;
  private Network network;
  private Outcome outcome;
  private final Date startTime;
  private final List<AuditEvent.AuditEventEntityComponent> auditEventEntityList = new ArrayList<>();
  private Reference agentClientWho;

  public AuditEventBuilder(Date startTime) {
    this.startTime = startTime;
  }

  public AuditEventBuilder agentUserWho(Reference agentUserWho) {
    this.agentUserWho = agentUserWho;
    return this;
  }

  public AuditEventBuilder profileUrl(String profileUrl) {
    this.profileUrl = profileUrl;
    return this;
  }

  public AuditEventBuilder auditEventAction(AuditEvent.AuditEventAction auditEventAction) {
    this.auditEventAction = auditEventAction;
    return this;
  }

  public AuditEventBuilder restOperationType(RestOperationTypeEnum restOperationType) {
    this.restOperationType = restOperationType;
    return this;
  }

  public AuditEventBuilder fhirServerBaseUrl(String fhirServerBaseUrl) {
    this.fhirServerBaseUrl = fhirServerBaseUrl;
    return this;
  }

  public AuditEventBuilder agentUserPolicy(String agentUserPolicy) {
    this.agentUserPolicy = agentUserPolicy;
    return this;
  }

  public AuditEventBuilder requestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  public AuditEventBuilder agentServerTypeCoding(Coding agentServerTypeCoding) {
    this.agentServerTypeCoding = agentServerTypeCoding;
    return this;
  }

  public AuditEventBuilder agentClientTypeCoding(Coding agentClientTypeCoding) {
    this.agentClientTypeCoding = agentClientTypeCoding;
    return this;
  }

  public AuditEventBuilder network(Network network) {
    this.network = network;
    return this;
  }

  public AuditEventBuilder outcome(Outcome outcome) {
    this.outcome = outcome;
    return this;
  }

  public AuditEventBuilder agentClientWho(Reference agentClientWho) {
    this.agentClientWho = agentClientWho;
    return this;
  }

  public AuditEventBuilder addQuery(QueryEntity queryEntity) {
    AuditEvent.AuditEventEntityComponent queryEntityComponent =
        new AuditEvent.AuditEventEntityComponent();

    // uses https://hl7.org/fhir/R4/valueset-audit-entity-type.html
    queryEntityComponent
        .getType()
        .setSystem(AuditEntityType._2.getSystem())
        .setCode(AuditEntityType._2.toCode())
        .setDisplay(AuditEntityType._2.getDisplay());

    queryEntityComponent // uses https://hl7.org/fhir/R4/valueset-object-role.html
        .getRole()
        .setSystem(ObjectRole._24.getSystem())
        .setCode(ObjectRole._24.toCode())
        .setDisplay(ObjectRole._24.getDisplay());

    String description = queryEntity.getRequestType().name() + " " + queryEntity.getCompleteUrl();
    queryEntityComponent.setDescription(description);

    String queryString =
        queryEntity.getFhirServerBase()
            + "/"
            + queryEntity.getRequestPath()
            + generateQueryStringFromQueryParameters(queryEntity.getParameters());

    queryEntityComponent.getQueryElement().setValue(queryString.getBytes(StandardCharsets.UTF_8));

    auditEventEntityList.add(queryEntityComponent);
    return this;
  }

  private static String generateQueryStringFromQueryParameters(
      Map<String, String[]> queryStringParameters) {
    StringBuilder queryString = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String[]> nextEntrySet : queryStringParameters.entrySet()) {
      for (String nextValue : nextEntrySet.getValue()) {
        if (first) {
          queryString.append("?");
          first = false;
        } else {
          queryString.append("&");
        }
        queryString.append(UrlUtil.escapeUrlParam(nextEntrySet.getKey()));
        queryString.append("=");
        queryString.append(UrlUtil.escapeUrlParam(nextValue));
      }
    }
    return queryString.toString();
  }

  // TODO investigate https://hl7.org/fhir/R4/valueset-object-role.html variations to enhance
  public AuditEventBuilder addEntityWhat(
      BalpProfileEnum balpProfile, Boolean isPatientEntity, String entityWhatResourceId) {

    AuditEvent.AuditEventEntityComponent entity = new AuditEvent.AuditEventEntityComponent();

    if (isPatientEntity) {
      entity
          .getType()
          .setSystem(AuditEntityType._1.getSystem())
          .setCode(AuditEntityType._1.toCode())
          .setDisplay(AuditEntityType._1.getDisplay());
      entity
          .getRole()
          .setSystem(ObjectRole._1.getSystem())
          .setCode(ObjectRole._1.toCode())
          .setDisplay(ObjectRole._1.getDisplay());

    } else {

      entity
          .getType()
          .setSystem(AuditEntityType._2.getSystem())
          .setCode(AuditEntityType._2.toCode())
          .setDisplay(AuditEntityType._2.getDisplay());
      entity
          .getRole()
          .setSystem(ObjectRole._4.getSystem())
          .setCode(ObjectRole._4.toCode())
          .setDisplay(ObjectRole._4.getDisplay());
    }

    if (balpProfile == BalpProfileEnum.BASIC_DELETE
        || balpProfile == BalpProfileEnum.PATIENT_DELETE) {
      entity.setWhat(createDeletedResourceRef(entityWhatResourceId));
    } else {
      entity.getWhat().setReference(entityWhatResourceId);
    }

    auditEventEntityList.add(entity);
    return this;
  }

  AuditEvent build() {
    AuditEvent auditEvent = new AuditEvent();
    auditEvent.getMeta().addProfile(this.profileUrl);
    auditEvent
        .getText()
        .setDiv(
            new XhtmlNode().setValue("<div>BALP Audit Event Generated by FHIR Info Gateway</div>"))
        .setStatus(Narrative.NarrativeStatus.GENERATED);
    auditEvent
        .getType()
        .setSystem(AuditEventType.REST.getSystem())
        .setCode(AuditEventType.REST.toCode())
        .setDisplay(AuditEventType.REST.getDisplay());
    auditEvent
        .addSubtype()
        .setSystem(RestfulInteraction.fromCode(this.restOperationType.getCode()).getSystem())
        .setCode(this.restOperationType.getCode())
        .setDisplay(RestfulInteraction.fromCode(this.restOperationType.getCode()).getDisplay());
    auditEvent.setAction(this.auditEventAction);
    if (this.outcome != null) {
      auditEvent.setOutcome(this.outcome.code);
      auditEvent.setOutcomeDesc(this.outcome.description);
    } else {
      auditEvent.setOutcome(AuditEvent.AuditEventOutcome._0);
      auditEvent.setOutcomeDesc(AuditEvent.AuditEventOutcome._0.getDisplay());
    }

    auditEvent.setRecorded(new Date());

    auditEvent.getSource().getObserver().setDisplay(this.fhirServerBaseUrl);

    AuditEvent.AuditEventAgentComponent clientAgent = auditEvent.addAgent();
    clientAgent.setWho(this.agentClientWho);
    clientAgent.getType().addCoding(this.agentClientTypeCoding);
    clientAgent.setRequestor(false);

    AuditEvent.AuditEventAgentComponent serverAgent = auditEvent.addAgent();
    serverAgent.getType().addCoding(this.agentServerTypeCoding);
    serverAgent.getWho().setDisplay(this.fhirServerBaseUrl);
    serverAgent.getNetwork().setAddress(this.fhirServerBaseUrl);
    serverAgent.setRequestor(false);

    AuditEvent.AuditEventAgentComponent userAgent = auditEvent.addAgent();
    userAgent
        .getType()
        .addCoding()
        .setSystem(V3ParticipationType.IRCP.getSystem())
        .setCode(V3ParticipationType.IRCP.toCode())
        .setDisplay(V3ParticipationType.IRCP.getDisplay());
    userAgent.setWho(this.agentUserWho);
    userAgent.setRequestor(true);
    userAgent.addPolicy(this.agentUserPolicy);
    userAgent.getNetwork().setAddress(this.network.address).setType(this.network.type);

    AuditEvent.AuditEventEntityComponent entityTransaction = auditEvent.addEntity();
    entityTransaction
        .getType()
        .setSystem(CS_BALP_BASIC_AUDIT_ENTITY_TYPE)
        .setCode(CS_BALP_BASIC_AUDIT_ENTITY_TYPE_CODE);
    entityTransaction.getWhat().getIdentifier().setValue(this.requestId);

    Period period = new Period();
    period.setStart(this.startTime);
    auditEvent.setPeriod(period);

    for (AuditEvent.AuditEventEntityComponent auditEventEntityComponent : auditEventEntityList) {
      auditEvent.addEntity(auditEventEntityComponent);
    }

    return auditEvent;
  }

  private Reference createDeletedResourceRef(String resourceId) {
    String resourceType = resourceId.substring(0, resourceId.indexOf('/'));
    return new Reference()
        .setType(resourceType)
        .setIdentifier(new Identifier().setSystem(CS_INFO_GATEWAY_DELETED).setValue(resourceId));
  }

  @Builder
  public static class Network {
    private String address;
    private AuditEvent.AuditEventAgentNetworkType type;
  }

  @Builder
  public static class Outcome {
    private AuditEvent.AuditEventOutcome code;
    private String description;
  }

  @Builder
  @Getter
  public static class QueryEntity {
    private RequestTypeEnum requestType;
    private String completeUrl;
    private String fhirServerBase;
    private String requestPath;
    private Map<String, String[]> parameters;
  }

  @Builder
  @Getter
  public static class ResourceContext {
    private QueryEntity queryEntity;
    private IBaseResource resourceEntity;
  }
}
