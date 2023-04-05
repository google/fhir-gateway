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

  // Additional query parameters that should be added to the outgoing FHIR request
  @Builder.Default Map<String, List<String>> queryParams = new HashMap<>();
}
