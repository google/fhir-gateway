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
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.StringType;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/LocationHeirarchy")
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

    //        SearchParameterMap paramMap = new SearchParameterMap();
    //        paramMap.add(IDENTIFIER, identifier);

    //        IBundleProvider locationBundle = locationIFhirResourceDao.search(paramMap);
    IBundleProvider locationBundle = null;
    //    try {
    //      HttpUtils.sendGET("http://localhost:8090/fhir/Location?identifier=" + identifier);
    //    } catch (IOException e) {
    //      throw new RuntimeException(e);
    //    }
    List<IBaseResource> locations =
        locationBundle != null
            ? locationBundle.getResources(0, locationBundle.size())
            : new ArrayList<>();
    String locationId = EMPTY_STRING;
    if (locations.size() > 0
        && locations.get(0) != null
        && locations.get(0).getIdElement() != null) {
      locationId = locations.get(0).getIdElement().getIdPart();
    }

    LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
    LocationHierarchy locationHierarchy = new LocationHierarchy();
    if (StringUtils.isNotBlank(locationId) && locations.size() > 0) {
      logger.info("Building Location Hierarchy of Location Id : " + locationId);
      locationHierarchyTree.buildTreeFromList(getLocationHierarchy(locationId, locations.get(0)));
      StringType locationIdString = new StringType().setId(locationId).getIdElement();
      locationHierarchy.setLocationId(locationIdString);
      locationHierarchy.setId(LOCATION_RESOURCE + locationId);

      locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
    } else {
      locationHierarchy.setId(LOCATION_RESOURCE_NOT_FOUND);
    }
    return locationHierarchy;
  }

  private List<Location> getLocationHierarchy(String locationId, IBaseResource parentLocation) {
    return descendants(locationId, parentLocation);
  }

  public List<Location> descendants(String locationId, IBaseResource parentLocation) {

    //        SearchParameterMap paramMap = new SearchParameterMap();
    //        ReferenceAndListParam thePartOf = new ReferenceAndListParam();
    //        ReferenceParam partOf = new ReferenceParam();
    //        partOf.setValue(LOCATION + FORWARD_SLASH + locationId);
    //        ReferenceOrListParam referenceOrListParam = new ReferenceOrListParam();
    //        referenceOrListParam.add(partOf);
    //        thePartOf.addValue(referenceOrListParam);
    //        paramMap.add(PART_OF, thePartOf);

    //        IBundleProvider childLocationBundle = locationIFhirResourceDao.search(paramMap);
    IBundleProvider childLocationBundle = null;

    //    try {
    //      HttpUtils.sendGET(
    //          "http://localhost:8090/fhir/Location?partof=" + LOCATION + FORWARD_SLASH +
    // locationId);
    //    } catch (IOException e) {
    //      throw new RuntimeException(e);
    //    }
    List<Location> allLocations = new ArrayList<>();
    if (parentLocation != null) {
      allLocations.add((Location) parentLocation);
    }
    if (childLocationBundle != null) {
      for (IBaseResource childLocation :
          childLocationBundle.getResources(0, childLocationBundle.size())) {
        Location childLocationEntity = (Location) childLocation;
        allLocations.add(childLocationEntity);
        allLocations.addAll(descendants(childLocation.getIdElement().getIdPart(), null));
      }
    }

    return allLocations;
  }

  //    public void setLocationIFhirResourceDao(IFhirResourceDao<Location> locationIFhirResourceDao)
  // {
  //        this.locationIFhirResourceDao = locationIFhirResourceDao;
  //    }
}
