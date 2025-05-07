package com.google.fhir.gateway.plugin.audit;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditEventSink;
import org.hl7.fhir.r4.model.AuditEvent;

public class BalpAuditEventSink implements IBalpAuditEventSink {

  private final IGenericClient genericClient;

  public BalpAuditEventSink(IGenericClient genericClient) {
    this.genericClient = genericClient;
  }

  @Override
  public void recordAuditEvent(AuditEvent auditEvent) {
    genericClient.create().resource(auditEvent).prettyPrint().encodedJson().execute();
  }
}
