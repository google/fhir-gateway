package com.google.fhir.proxy;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * This is the default no-op access-checker which lets all requests to go through.
 */
public class PermissiveAccessChecker implements PatientAccessChecker {
  public boolean canAccessPatient(String patientId) {
    return true;
  }

  public static class Factory implements PatientAccessCheckerFactory {
    @Override
    public PatientAccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      return new PermissiveAccessChecker();
    }
  }
}
