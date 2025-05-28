package com.google.fhir.gateway;

import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.storage.interceptor.balp.BalpProfileEnum;
import ca.uhn.fhir.util.UrlUtil;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.*;
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

  private AuditEventBuilder() {
    this.startTime = null;
  }

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

  public AuditEventBuilder addQuery(RequestDetailsReader requestDetailsReader) {
    AuditEvent.AuditEventEntityComponent queryEntity = new AuditEvent.AuditEventEntityComponent();

    // uses https://hl7.org/fhir/R4/valueset-audit-entity-type.html
    queryEntity
        .getType()
        .setSystem(AuditEntityType._2.getSystem())
        .setCode(AuditEntityType._2.toCode())
        .setDisplay(AuditEntityType._2.getDisplay());

    queryEntity // uses https://hl7.org/fhir/R4/valueset-object-role.html
        .getRole()
        .setSystem(ObjectRole._24.getSystem())
        .setCode(ObjectRole._24.toCode())
        .setDisplay(ObjectRole._24.getDisplay());

    String description =
        requestDetailsReader.getRequestType().name() + " " + requestDetailsReader.getCompleteUrl();
    queryEntity.setDescription(description);

    StringBuilder queryString = new StringBuilder();
    queryString.append(requestDetailsReader.getFhirServerBase());
    queryString.append("/");
    queryString.append(requestDetailsReader.getRequestPath());
    boolean first = true;
    for (Map.Entry<String, String[]> nextEntrySet :
        requestDetailsReader.getParameters().entrySet()) {
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

    queryEntity.getQueryElement().setValue(queryString.toString().getBytes(StandardCharsets.UTF_8));

    auditEventEntityList.add(queryEntity);
    return this;
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
        .setDiv(new XhtmlNode().setValue("<div>Audit Event</div>"))
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
    auditEvent.setOutcome(
        this.outcome != null ? this.outcome.code : AuditEvent.AuditEventOutcome._0);
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
}
