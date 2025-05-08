package com.google.fhir.gateway.plugin.audit;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditContextServices;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditEventSink;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.Nullable;

public class BalpAccessDecision implements AccessDecision {

  private final BalpAuditEventSink eventSink;
  private final IBalpAuditContextServices contextServices;
  private AccessDecision accessDecision;

  public BalpAccessDecision(IGenericClient genericClient) {
    this.eventSink = new BalpAuditEventSink(genericClient);
    this.contextServices = new BalpAuditContextService();
  }

  @Override
  public boolean canAccess() {
    return accessDecision.canAccess();
  }

  @Override
  public @Nullable RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {
    return accessDecision.getRequestMutation(requestDetailsReader);
  }

  @Override
  public String postProcess(RequestDetailsReader request, HttpResponse response)
      throws IOException {
    return accessDecision.postProcess(request, response);
  }

  @Override
  public @Nullable IBalpAuditEventSink getBalpAuditEventSink() {
    return this.eventSink;
  }

  @Override
  public @Nullable IBalpAuditContextServices getBalpAuditContextService() {
    return this.contextServices;
  }

  public BalpAccessDecision withAccess(AccessDecision accessDecision) {
    this.accessDecision = accessDecision;
    return this;
  }
}
