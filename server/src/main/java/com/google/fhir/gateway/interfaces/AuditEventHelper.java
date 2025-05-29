package com.google.fhir.gateway.interfaces;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;
import org.hl7.fhir.r4.model.Reference;

public interface AuditEventHelper {
  void processAuditEvents(
      RequestDetailsReader requestDetailsReader,
      String serverContentResponseReader,
      Reference agentUserWho);

  void setPeriodStartTime(Date startTime);

  void setDecodedJwt(DecodedJWT decodedJwt);
}
