package com.google.fhir.proxy;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * This is the default no-op access-checker which lets all requests to go through.
 */
public class PermissiveAccessChecker implements AccessChecker {
  @Override
  public boolean canAccess(RequestDetails requestDetails) {
    return true;
  }

  public static class Factory implements AccessCheckerFactory {
    @Override
    public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      return new PermissiveAccessChecker();
    }
  }
}
