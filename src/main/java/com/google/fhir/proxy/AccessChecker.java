package com.google.fhir.proxy;

import ca.uhn.fhir.rest.api.server.RequestDetails;

interface AccessChecker {

  boolean canAccess(RequestDetails requestDetails);
}
