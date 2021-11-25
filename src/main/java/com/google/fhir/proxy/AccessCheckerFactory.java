package com.google.fhir.proxy;

import com.auth0.jwt.interfaces.DecodedJWT;

public interface AccessCheckerFactory {

    AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient);
}
