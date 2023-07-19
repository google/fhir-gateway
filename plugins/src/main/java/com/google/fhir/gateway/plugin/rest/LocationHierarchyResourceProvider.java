/*
 * Copyright 2021-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.gateway.plugin.rest;

import static org.smartregister.utils.Constants.*;

import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import java.util.logging.Logger;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.StringType;
import org.smartregister.model.location.LocationHierarchy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/LocationHierarchy")
public class LocationHierarchyResourceProvider implements IResourceProvider {

  private static final Logger logger =
      Logger.getLogger(LocationHierarchyResourceProvider.class.toString());

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return LocationHierarchy.class;
  }

  @GetMapping
  public LocationHierarchy getLocationHierarchy(
      @RequiredParam(name = IDENTIFIER) TokenParam identifier) {
    LocationHierarchy locationHierarchy = new LocationHierarchy();
    StringType id = new StringType();
    id.setId("1");
    locationHierarchy.setLocationId(id);
    return locationHierarchy;
  }
}
