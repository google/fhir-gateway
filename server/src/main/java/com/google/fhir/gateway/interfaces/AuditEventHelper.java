package com.google.fhir.gateway.interfaces;

import org.hl7.fhir.r4.model.Reference;

public interface AuditEventHelper {
  void processAuditEvents(
      RequestDetailsReader requestDetailsReader,
      String serverContentResponseReader,
      Reference userWho);
}
