package com.google.fhir.gateway.interfaces;

import com.google.fhir.gateway.AuditEventHelperImpl;

public interface AuditEventHelper {
  void processAuditEvents(AuditEventHelperImpl.AuditEventHelperInputDto auditEventHelperInputDto);
}
