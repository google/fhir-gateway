package com.demo;

import ca.uhn.fhir.context.FhirContext;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import javax.inject.Named;

/** Simple access checker to only allow patient list operation. */
public class DemoAccessChecker implements AccessChecker {

  DemoAccessChecker() {}

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    if (requestDetails.getRequestPath().equalsIgnoreCase("patient")) {
      return NoOpAccessDecision.accessGranted();
    }
    return NoOpAccessDecision.accessDenied();
  }

  @Named(value = "demo")
  public static class Factory implements AccessCheckerFactory {

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      return new DemoAccessChecker();
    }
  }
}
