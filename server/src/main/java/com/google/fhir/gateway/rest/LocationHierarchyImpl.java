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
package com.google.fhir.gateway.rest;

import static com.google.fhir.gateway.util.Constants.PROXY_TO_ENV;
import static org.smartregister.utils.Constants.*;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

public class LocationHierarchyImpl {

  private FhirContext fhirR4Context = FhirContext.forR4();

  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

  private static final Logger logger = LoggerFactory.getLogger(LocationHierarchyImpl.class);

  private IGenericClient r4FhirClient =
      fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));

  private IGenericClient getFhirClientForR4() {
    return r4FhirClient;
  }

  public LocationHierarchy getLocationHierarchy(String identifier) {
    Location location = getLocationsByIdentifier(identifier);
    String locationId = EMPTY_STRING;
    if (location != null && location.getIdElement() != null) {
      locationId = location.getIdElement().getIdPart();
    }

    LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
    LocationHierarchy locationHierarchy = new LocationHierarchy();
    if (StringUtils.isNotBlank(locationId) && location != null) {
      logger.info("Building Location Hierarchy of Location Id : " + locationId);
      locationHierarchyTree.buildTreeFromList(getLocationHierarchy(locationId, location));
      StringType locationIdString = new StringType().setId(locationId).getIdElement();
      locationHierarchy.setLocationId(locationIdString);
      locationHierarchy.setId(LOCATION_RESOURCE + locationId);

      locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
    } else {
      locationHierarchy.setId(LOCATION_RESOURCE_NOT_FOUND);
    }
    return locationHierarchy;
  }

  private List<Location> getLocationHierarchy(String locationId, Location parentLocation) {
    return descendants(locationId, parentLocation);
  }

  public List<Location> descendants(String locationId, Location parentLocation) {

    Bundle childLocationBundle =
        getFhirClientForR4()
            .search()
            .forResource(Patient.class)
            .where(new ReferenceClientParam(Location.SP_PARTOF).hasAnyOfIds(locationId))
            .returnBundle(Bundle.class)
            .execute();

    List<Location> allLocations = new ArrayList<>();
    if (parentLocation != null) {
      allLocations.add((Location) parentLocation);
    }

    if (childLocationBundle != null) {
      for (Bundle.BundleEntryComponent childLocation : childLocationBundle.getEntry()) {
        Location childLocationEntity = (Location) childLocation.getResource();
        allLocations.add(childLocationEntity);
        allLocations.addAll(descendants(childLocationEntity.getIdElement().getIdPart(), null));
      }
    }

    return allLocations;
  }

  private @Nullable List<Location> getLocationsByIds(List<String> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return new ArrayList<>();
    }

    Bundle locationsBundle =
        getFhirClientForR4()
            .search()
            .forResource(Location.class)
            .where(new ReferenceClientParam(BaseResource.SP_RES_ID).hasAnyOfIds(locationIds))
            .returnBundle(Bundle.class)
            .execute();

    return locationsBundle.getEntry().stream()
        .map(bundleEntryComponent -> ((Location) bundleEntryComponent.getResource()))
        .collect(Collectors.toList());
  }

  private @Nullable Location getLocationsByIdentifier(String identifier) {
    Bundle locationsBundle =
        getFhirClientForR4()
            .search()
            .forResource(Patient.class)
            .where(new TokenClientParam(Location.SP_IDENTIFIER).exactly().identifier(identifier))
            .returnBundle(Bundle.class)
            .execute();

    List<Location> locationsList = new ArrayList<>();
    locationsBundle.getEntry().stream()
        .map(bundleEntryComponent -> ((Location) bundleEntryComponent.getResource()))
        .collect(Collectors.toList());
    return locationsList.get(0);
  }
}
