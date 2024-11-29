# Create an access checker plugin

In this guide you will create your own access checker plugin.

## Implement the `AccessCheckerFactory` interface

To create your own access checker plugin, create an implementation of
the [`AccessCheckerFactory` interface](https://github.com/google/fhir-gateway/blob/main/server/src/main/java/com/google/fhir/gateway/interfaces/AccessCheckerFactory.java)
annotated with a `@Named(value = "name")` annotation defining the name of the
plugin.

The most important parts are to implement a
custom [`AccessChecker`](https://github.com/google/fhir-gateway/blob/main/server/src/main/java/com/google/fhir/gateway/interfaces/AccessChecker.java)
to be returned by the factory and its `checkAccess` function which specifies if
access is granted or not by returning
an [`AccessDecision`](https://github.com/google/fhir-gateway/blob/main/server/src/main/java/com/google/fhir/gateway/interfaces/AccessDecision.java).

## Create a new class

The simplest way to create your own access checker is to make a new class file
in the `plugins/src/main/java/com/google/fhir/gateway/plugin` directory, next to
the existing sample plugins. The following code can be used as a starting
template for a minimal access checker:

```java
package com.google.fhir.gateway.plugin;

import ca.uhn.fhir.context.FhirContext;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.FhirUtil;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import javax.inject.Named;

public class MyAccessChecker implements AccessChecker {

  private final FhirContext fhirContext;
  private final HttpFhirClient httpFhirClient;
  private final String claim;
  private final PatientFinder patientFinder;

  // We're not using any of the parameters here, but real access checkers
  // would likely use some/all.
  private MyAccessChecker(
      HttpFhirClient httpFhirClient,
      String claim,
      FhirContext fhirContext,
      PatientFinder patientFinder) {
    this.fhirContext = fhirContext;
    this.claim = claim;
    this.httpFhirClient = httpFhirClient;
    this.patientFinder = patientFinder;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    // Implement your access logic here.
    return NoOpAccessDecision.accessGranted();
  }

  // The factory must be thread-safe but the AccessChecker instances it returns
  // do not need to be thread-safe.
  @Named(value = "sample")
  public static class Factory implements AccessCheckerFactory {

    static final String CLAIM = "sub";

    private String getClaim(DecodedJWT jwt) {
      return FhirUtil.checkIdOrFail(JwtUtil.getClaimOrDie(jwt, CLAIM));
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      String claim = getClaim(jwt);
      return new MyAccessChecker(httpFhirClient, claim, fhirContext, patientFinder);
    }
  }
}

```

## Rebuild to include the plugin
Once you're done implementing your access checker plugin, rebuild using
`mvn package` from the root of the project to include the plugin, set the
access-checker using e.g. `export ACCESS_CHECKER=sample`

## Run the gateway
Run the gateway using e.g.
`java -jar exec/target/exec-0.1.0.jar --server.port=8080`.