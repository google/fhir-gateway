package com.google.fhir.proxy;

import com.auth0.jwt.interfaces.DecodedJWT;

public interface PatientAccessCheckerFactory {

    PatientAccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient);
}
