package com.google.fhir.gateway.interfaces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** Defines mutations that can be applied to the incoming request by an {@link AccessChecker}. */
@Builder
@Getter
public class RequestMutation {

  // Additional query parameters and list of values for a parameter that should be added to the
  // outgoing FHIR request.
  // New values overwrites the old one if there is a conflict for a request param (i.e. a returned
  // parameter in RequestMutation is already present in the original request).
  // Old parameter values should be explicitly retained while mutating values for that parameter.
  @Builder.Default Map<String, List<String>> queryParams = new HashMap<>();
}
