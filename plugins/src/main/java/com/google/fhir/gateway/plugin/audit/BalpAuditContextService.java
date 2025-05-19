package com.google.fhir.gateway.plugin.audit;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditContextServices;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.JwtUtil;
import jakarta.annotation.Nonnull;
import org.apache.http.HttpHeaders;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.jetbrains.annotations.NotNull;

public class BalpAuditContextService implements IBalpAuditContextServices {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CLAIM_NAME = "name";
  private static final String CLAIM_PREFERRED_NAME = "preferred_username";
  private static final String CLAIM_SUBJECT = "sub";

  @Override
  public @NotNull Reference getAgentClientWho(RequestDetails requestDetails) {

    return new Reference()
        // .setReference("Device/fhir-info-gateway")
        .setType("Device")
        .setDisplay("FHIR Info Gateway")
        .setIdentifier(
            new Identifier()
                .setSystem("http://fhir-info-gateway/devices")
                .setValue("fhir-info-gateway-001"));
  }

  @Override
  public @NotNull Reference getAgentUserWho(RequestDetails requestDetails) {

    String username = getClaimIfExists(requestDetails, CLAIM_PREFERRED_NAME);
    String name = getClaimIfExists(requestDetails, CLAIM_NAME);
    String subject = getClaimIfExists(requestDetails, CLAIM_SUBJECT);

    return new Reference()
        // .setReference("Practitioner/" + subject)
        .setType("Practitioner")
        .setDisplay(name)
        .setIdentifier(
            new Identifier()
                .setSystem("http://fhir-info-gateway/practitioners")
                .setValue(username));
  }

  @Override
  public @NotNull String massageResourceIdForStorage(
      @Nonnull RequestDetails theRequestDetails,
      @Nonnull IBaseResource theResource,
      @Nonnull IIdType theResourceId) {

    /**
     * Server not configured to allow external references resulting to InvalidRequestException: HTTP
     * 400 : HAPI-0507: Resource contains external reference to URL. Here we should use relative
     * references instead e.g. Patient/123;
     */
    // String serverBaseUrl = theRequestDetails.getFhirServerBase();
    return theRequestDetails.getId() != null
        ? theRequestDetails.getId().getValue()
        : ""; // For entity POST there will be no agent.who entry reference since not generated yet
  }

  public String getClaimIfExists(RequestDetails requestDetails, String claimName) {
    String claim;
    try {
      String authHeader = requestDetails.getHeader(HttpHeaders.AUTHORIZATION);
      String bearerToken = authHeader.substring(BEARER_PREFIX.length());
      DecodedJWT jwt;

      jwt = JWT.decode(bearerToken);
      claim = JwtUtil.getClaimOrDie(jwt, claimName);
    } catch (JWTDecodeException | AuthenticationException e) {
      claim = "";
    }
    return claim;
  }
}
