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
/// *
// * Copyright 2021-2023 Google LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *       http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package com.google.fhir.gateway.plugin.rest;
////
//// import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
//// import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
// import static org.smartregister.utils.Constants.*;
//
// import ca.uhn.fhir.rest.annotation.RequiredParam;
// import ca.uhn.fhir.rest.param.*;
// import ca.uhn.fhir.rest.server.IResourceProvider;
// import java.io.BufferedReader;
// import java.io.IOException;
// import java.io.InputStreamReader;
// import java.net.HttpURLConnection;
// import java.net.URL;
// import java.util.*;
// import java.util.logging.Logger;
// import org.hl7.fhir.instance.model.api.IBaseResource;
// import org.hl7.fhir.r4.model.*;
// import org.smartregister.model.location.LocationHierarchy;
// import org.smartregister.model.practitioner.FhirPractitionerDetails;
// import org.smartregister.model.practitioner.PractitionerDetails;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.RestController;
//
// @RestController("/PractitionerDetails")
// public class PractitionerDetailsResourceProvider implements IResourceProvider {
//
//  //    @Autowired private IFhirResourceDao<Practitioner> practitionerIFhirResourceDao;
//  //
//  //    @Autowired private IFhirResourceDao<PractitionerRole> practitionerRoleIFhirResourceDao;
//  //
//  //    @Autowired private IFhirResourceDao<CareTeam> careTeamIFhirResourceDao;
//  //
//  //    @Autowired
//  //    private IFhirResourceDao<OrganizationAffiliation> organizationAffiliationIFhirResourceDao;
//  //
//  //    @Autowired private IFhirResourceDao<Organization> organizationIFhirResourceDao;
//  //
//  //    @Autowired private IFhirResourceDao<Group> groupIFhirResourceDao;
//  //
//  //    @Autowired private LocationHierarchyResourceProvider locationHierarchyResourceProvider;
//  //
//  //    @Autowired private IFhirResourceDao<Location> locationIFhirResourceDao;
//
//  private static final String KEYCLOAK_UUID = "keycloak-uuid";
//
//  private static final Logger logger =
//      Logger.getLogger(PractitionerDetailsResourceProvider.class.getName());
//
//  @Override
//  public Class<? extends IBaseResource> getResourceType() {
//    return PractitionerDetails.class;
//  }
//
//  @GetMapping("/")
//  public PractitionerDetails getPractitionerDetails(
//      @RequiredParam(name = KEYCLOAK_UUID) TokenParam identifier) {
//    PractitionerDetails practitionerDetails = new PractitionerDetails();
//    FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
//    StringType pId = new StringType();
//    pId.setId("hey");
//    fhirPractitionerDetails.setPractitionerId(pId);
//    practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
//    try {
//      sendGET("http://localhost:8090/fhir/Organization");
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//    return practitionerDetails;
//  }
//
//
//  //        SearchParameterMap paramMap = new SearchParameterMap();
//  //        paramMap.add(IDENTIFIER, identifier);
//  //        logger.info("Searching for practitioner with identifier: " + identifier.getValue());
//  //        IBundleProvider practitionerBundle = practitionerIFhirResourceDao.search(paramMap);
//  //        List<IBaseResource> practitioners =
//  //                practitionerBundle != null
//  //                        ? practitionerBundle.getResources(0, practitionerBundle.size())
//  //                        : new ArrayList<>();
//  //
//  //        IBaseResource practitioner =
//  //                practitioners.size() > 0 ? practitioners.get(0) : new Practitioner();
//  //        String practitionerId = EMPTY_STRING;
//  //        if (practitioner.getIdElement() != null
//  //                && practitioner.getIdElement().getIdPart() != null) {
//  //            practitionerId = practitioner.getIdElement().getIdPart();
//  //        }
//  //
//  //        if (StringUtils.isNotBlank(practitionerId)) {
//  //            logger.info("Searching for care teams for practitioner with id: " +
// practitionerId);
//  //            List<IBaseResource> careTeams = getCareTeams(practitionerId);
//  //            List<CareTeam> careTeamsList = mapToCareTeams(careTeams);
//  //            fhirPractitionerDetails.setCareTeams(careTeamsList);
//  //            StringType practitionerIdString = new StringType();
//  //            practitionerIdString.setValue(practitionerId);
//  //            fhirPractitionerDetails.setPractitionerId(practitionerIdString);
//  //
//  //            logger.info("Searching for Organizations tied with CareTeams: ");
//  //            List<IBaseResource> managingOrganizationsOfCareTeams =
//  //                    getManagingOrganizationsOfCareTeams(careTeamsList);
//  //            logger.info("Managing Organization are fetched");
//  //            List<Organization> managingOrganizationTeams =
//  //                    mapToTeams(managingOrganizationsOfCareTeams);
//  //
//  //            logger.info("Searching for organizations of practitioner with id: " +
//  // practitionerId);
//  //            List<IBaseResource> organizationTeams =
//  // getOrganizationsOfPractitioner(practitionerId);
//  //            logger.info("Organizations are fetched");
//  //            List<Organization> teams = mapToTeams(organizationTeams);
//  //
//  //            List<Organization> bothOrganizations;
//  //            // Add items from Lists into Set
//  //            Set<Organization> set = new LinkedHashSet<>(managingOrganizationTeams);
//  //            set.addAll(teams);
//  //
//  //            bothOrganizations = new ArrayList<>(set);
//  //
//  //            fhirPractitionerDetails.setOrganizations(bothOrganizations);
//  //
//  //            List<IBaseResource> practitionerRoles =
//  //                    getPractitionerRolesOfPractitioner(practitionerId);
//  //            logger.info("Practitioner Roles are fetched");
//  //            List<PractitionerRole> practitionerRoleList =
//  // mapToPractitionerRoles(practitionerRoles);
//  //            fhirPractitionerDetails.setPractitionerRoles(practitionerRoleList);
//  //
//  //            List<IBaseResource> groups = getGroupsAssignedToAPractitioner(practitionerId);
//  //            logger.info("Groups are fetched");
//  //            List<Group> groupsList = mapToGroups(groups);
//  //            fhirPractitionerDetails.setGroups(groupsList);
//  //            fhirPractitionerDetails.setId(practitionerIdString.getValue());
//  //
//  //            logger.info("Searching for locations by organizations");
//  //            List<String> locationsIdReferences =
//  //                    getLocationIdentifiersByOrganizations(bothOrganizations);
//  //            List<String> locationIds = getLocationIdsFromReferences(locationsIdReferences);
//  //            List<String> locationsIdentifiers = getLocationIdentifiersByIds(locationIds);
//  //            logger.info("Searching for location hierarchy list by locations identifiers");
//  //            List<LocationHierarchy> locationHierarchyList =
//  //                    getLocationsHierarchy(locationsIdentifiers);
//  //            fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);
//  //            logger.info("Searching for locations by ids");
//  //            List<Location> locationsList = getLocationsByIds(locationIds);
//  //            fhirPractitionerDetails.setLocations(locationsList);
//  //            practitionerDetails.setId(practitionerId);
//  //            practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
//  //        } else {
//  //            logger.error("Practitioner with identifier: " + identifier.getValue() + " not
//  // found");
//  //            practitionerDetails.setId(PRACTITIONER_NOT_FOUND);
//  //        }
//  //
//  //        return practitionerDetails;
//  //    }
//  //
//  //    private List<IBaseResource> getCareTeams(String practitionerId) {
//  //        SearchParameterMap careTeamSearchParameterMap = new SearchParameterMap();
//  //        ReferenceParam participantReference = new ReferenceParam();
//  //        participantReference.setValue(practitionerId);
//  //        ReferenceOrListParam careTeamReferenceParameter = new ReferenceOrListParam();
//  //        careTeamReferenceParameter.addOr(participantReference);
//  //        careTeamSearchParameterMap.add(PARTICIPANT, careTeamReferenceParameter);
//  //        IBundleProvider careTeamsBundle =
//  //                careTeamIFhirResourceDao.search(careTeamSearchParameterMap);
//  //        return careTeamsBundle != null
//  //                ? careTeamsBundle.getResources(0, careTeamsBundle.size())
//  //                : new ArrayList<>();
//  //    }
//  //
//  //    private List<Location> getLocationsByIds(List<String> locationIds) {
//  //        List<Location> locations = new ArrayList<>();
//  //        SearchParameterMap searchParameterMap = new SearchParameterMap();
//  //        for (String locationId : locationIds) {
//  //            Location location;
//  //            for (IBaseResource locationResource :
//  //                    generateLocationResource(searchParameterMap, locationId)) {
//  //                location = (Location) locationResource;
//  //                locations.add(location);
//  //            }
//  //        }
//  //        return locations;
//  //    }
//  //
//  private List<CareTeam> mapToCareTeams(List<IBaseResource> careTeams) {
//    List<CareTeam> careTeamList = new ArrayList<>();
//    CareTeam careTeamObject;
//    for (IBaseResource careTeam : careTeams) {
//      careTeamObject = (CareTeam) careTeam;
//      careTeamList.add(careTeamObject);
//    }
//    return careTeamList;
//  }
//  //
//  //    private List<IBaseResource> getPractitionerRolesOfPractitioner(String practitionerId) {
//  //        SearchParameterMap practitionerRoleSearchParamMap = new SearchParameterMap();
//  //        ReferenceParam practitionerReference = new ReferenceParam();
//  //        practitionerReference.setValue(practitionerId);
//  //        ReferenceOrListParam careTeamReferenceParameter = new ReferenceOrListParam();
//  //        careTeamReferenceParameter.addOr(practitionerReference);
//  //        practitionerRoleSearchParamMap.add(PRACTITIONER, practitionerReference);
//  //        logger.info("Searching for Practitioner roles  with practitioner id :" +
//  // practitionerId);
//  //        IBundleProvider practitionerRoleBundle =
//  //                practitionerRoleIFhirResourceDao.search(practitionerRoleSearchParamMap);
//  //        return practitionerRoleBundle != null
//  //                ? practitionerRoleBundle.getResources(0, practitionerRoleBundle.size())
//  //                : new ArrayList<>();
//  //    }
//  //
//  //    private List<String> getOrganizationIds(String practitionerId) {
//  //        List<IBaseResource> practitionerRoles =
//  // getPractitionerRolesOfPractitioner(practitionerId);
//  //        List<String> organizationIdsString = new ArrayList<>();
//  //        if (practitionerRoles.size() > 0) {
//  //            for (IBaseResource practitionerRole : practitionerRoles) {
//  //                PractitionerRole pRole = (PractitionerRole) practitionerRole;
//  //                if (pRole.getOrganization() != null
//  //                        && pRole.getOrganization().getReference() != null) {
//  //                    organizationIdsString.add(pRole.getOrganization().getReference());
//  //                }
//  //            }
//  //        }
//  //        return organizationIdsString;
//  //    }
//  //
//  //    private List<IBaseResource> getOrganizationsOfPractitioner(String practitionerId) {
//  //        List<String> organizationIdsReferences = getOrganizationIds(practitionerId);
//  //        logger.info(
//  //                "Organization Ids are retrieved, found to be of size: "
//  //                        + organizationIdsReferences.size());
//  //
//  //        return searchOrganizationsById(organizationIdsReferences);
//  //    }
//  //
//  //    private List<IBaseResource> searchOrganizationsById(List<String>
// organizationIdsReferences)
//  // {
//  //        List<String> organizationIds =
//  // getOrganizationIdsFromReferences(organizationIdsReferences);
//  //        SearchParameterMap organizationsSearchMap = new SearchParameterMap();
//  //        TokenAndListParam theId = new TokenAndListParam();
//  //        TokenOrListParam theIdList = new TokenOrListParam();
//  //        TokenParam id;
//  //        logger.info("Making a list of identifiers from organization identifiers");
//  //        IBundleProvider organizationsBundle;
//  //        if (organizationIds.size() > 0) {
//  //            for (String organizationId : organizationIds) {
//  //                id = new TokenParam();
//  //                id.setValue(organizationId);
//  //                theIdList.add(id);
//  //                logger.info("Added organization id : " + organizationId + " in a list");
//  //            }
//  //
//  //            theId.addAnd(theIdList);
//  //            organizationsSearchMap.add("_id", theId);
//  //            logger.info(
//  //                    "Now hitting organization search end point with the idslist param of size:
// "
//  //                            + theId.size());
//  //            organizationsBundle = organizationIFhirResourceDao.search(organizationsSearchMap);
//  //        } else {
//  //            return new ArrayList<>();
//  //        }
//  //        return organizationsBundle != null
//  //                ? organizationsBundle.getResources(0, organizationsBundle.size())
//  //                : new ArrayList<>();
//  //    }
//  //
//  private List<Organization> mapToTeams(List<IBaseResource> teams) {
//    List<Organization> organizations = new ArrayList<>();
//    Organization organizationObj;
//    for (IBaseResource team : teams) {
//      organizationObj = (Organization) team;
//      organizations.add(organizationObj);
//    }
//    return organizations;
//  }
//
//  private List<PractitionerRole> mapToPractitionerRoles(List<IBaseResource> practitionerRoles) {
//    List<PractitionerRole> practitionerRoleList = new ArrayList<>();
//    PractitionerRole practitionerRoleObj;
//    for (IBaseResource practitionerRole : practitionerRoles) {
//      practitionerRoleObj = (PractitionerRole) practitionerRole;
//      practitionerRoleList.add(practitionerRoleObj);
//    }
//    return practitionerRoleList;
//  }
//
//  private List<Group> mapToGroups(List<IBaseResource> groups) {
//    List<Group> groupList = new ArrayList<>();
//    Group groupObj;
//    for (IBaseResource group : groups) {
//      groupObj = (Group) group;
//      groupList.add(groupObj);
//    }
//    return groupList;
//  }
//
//      private List<LocationHierarchy> getLocationsHierarchy(List<String> locationsIdentifiers) {
//          List<LocationHierarchy> locationHierarchyList = new ArrayList<>();
//          TokenParam identifier;
//          LocationHierarchy locationHierarchy;
//          for (String locationsIdentifier : locationsIdentifiers) {
//              identifier = new TokenParam();
//              identifier.setValue(locationsIdentifier);
//              locationHierarchy =
//   locationHierarchyResourceProvider.getLocationHierarchy(identifier);
//              locationHierarchyList.add(locationHierarchy);
//          }
//          return locationHierarchyList;
//      }
//  //
//  //    private List<String> getLocationIdentifiersByOrganizations(List<Organization>
// organizations)
//  // {
//  //        List<String> locationsIdentifiers = new ArrayList<>();
//  //        Set<String> locationsIdentifiersSet = new HashSet<>();
//  //        SearchParameterMap searchParameterMap = new SearchParameterMap();
//  //        logger.info("Traversing organizations");
//  //        for (Organization team : organizations) {
//  //            ReferenceAndListParam thePrimaryOrganization = new ReferenceAndListParam();
//  //            ReferenceOrListParam primaryOrganizationRefParam = new ReferenceOrListParam();
//  //            ReferenceParam primaryOrganization = new ReferenceParam();
//  //            primaryOrganization.setValue(team.getId());
//  //            primaryOrganizationRefParam.addOr(primaryOrganization);
//  //            thePrimaryOrganization.addAnd(primaryOrganizationRefParam);
//  //            searchParameterMap.add(PRIMARY_ORGANIZATION, thePrimaryOrganization);
//  //            logger.info("Searching organization affiliation from organization id: " +
//  // team.getId());
//  //            IBundleProvider organizationsAffiliationBundle =
//  //                    organizationAffiliationIFhirResourceDao.search(searchParameterMap);
//  //            List<IBaseResource> organizationAffiliations =
//  //                    organizationsAffiliationBundle != null
//  //                            ? organizationsAffiliationBundle.getResources(
//  //                                    0, organizationsAffiliationBundle.size())
//  //                            : new ArrayList<>();
//  //            OrganizationAffiliation organizationAffiliationObj;
//  //            if (organizationAffiliations.size() > 0) {
//  //                for (IBaseResource organizationAffiliation : organizationAffiliations) {
//  //                    organizationAffiliationObj = (OrganizationAffiliation)
//  // organizationAffiliation;
//  //                    List<Reference> locationList = organizationAffiliationObj.getLocation();
//  //                    for (Reference location : locationList) {
//  //                        if (location != null
//  //                                && location.getReference() != null
//  //                                && locationsIdentifiersSet != null) {
//  //                            locationsIdentifiersSet.add(location.getReference());
//  //                        }
//  //                    }
//  //                }
//  //            }
//  //        }
//  //        locationsIdentifiers = new ArrayList<>(locationsIdentifiersSet);
//  //        return locationsIdentifiers;
//  //    }
//  //
//  //    private List<String> getLocationIdsFromReferences(List<String> locationReferences) {
//  //        return getResourceIds(locationReferences);
//  //    }
//  //
//  //    @NotNull
//  //    private List<String> getResourceIds(List<String> resourceReferences) {
//  //        List<String> resourceIds = new ArrayList<>();
//  //        for (String reference : resourceReferences) {
//  //            if (reference.contains(FORWARD_SLASH)) {
//  //                reference = reference.substring(reference.indexOf(FORWARD_SLASH) + 1);
//  //            }
//  //            resourceIds.add(reference);
//  //        }
//  //        return resourceIds;
//  //    }
//  //
//  //    private List<String> getOrganizationIdsFromReferences(List<String> organizationReferences)
// {
//  //        return getResourceIds(organizationReferences);
//  //    }
//  //
//  //    private List<String> getLocationIdentifiersByIds(List<String> locationIds) {
//  //        List<String> locationsIdentifiers = new ArrayList<>();
//  //        SearchParameterMap searchParameterMap = new SearchParameterMap();
//  //        for (String locationId : locationIds) {
//  //            List<IBaseResource> locationsResources =
//  //                    generateLocationResource(searchParameterMap, locationId);
//  //            Location locationObject;
//  //            for (IBaseResource locationResource : locationsResources) {
//  //                locationObject = (Location) locationResource;
//  //                locationsIdentifiers.addAll(
//  //                        locationObject.getIdentifier().stream()
//  //                                .map(this::getLocationIdentifierValue)
//  //                                .collect(Collectors.toList()));
//  //            }
//  //        }
//  //        return locationsIdentifiers;
//  //    }
//  //
//  //    private List<IBaseResource> generateLocationResource(
//  //            SearchParameterMap searchParameterMap, String locationId) {
//  //        TokenAndListParam idParam = new TokenAndListParam();
//  //        TokenParam id = new TokenParam();
//  //        id.setValue(String.valueOf(locationId));
//  //        idParam.addAnd(id);
//  //        searchParameterMap.add(ID, idParam);
//  //        IBundleProvider locationsBundle = locationIFhirResourceDao.search(searchParameterMap);
//  //
//  //        return locationsBundle != null
//  //                ? locationsBundle.getResources(0, locationsBundle.size())
//  //                : new ArrayList<>();
//  //    }
//  //
//  //    private List<IBaseResource> getGroupsAssignedToAPractitioner(String practitionerId) {
//  //        SearchParameterMap groupSearchParameterMap = new SearchParameterMap();
//  //        TokenAndListParam codeListParam = new TokenAndListParam();
//  //        TokenOrListParam coding = new TokenOrListParam();
//  //        TokenParam code = new TokenParam();
//  //
//  //        // Adding the code to the search parameters
//  //        code.setValue(PRACTITIONER_GROUP_CODE);
//  //        code.setSystem(HTTP_SNOMED_INFO_SCT);
//  //        coding.add(code);
//  //        codeListParam.addAnd(coding);
//  //        groupSearchParameterMap.add(CODE, codeListParam);
//  //        ReferenceAndListParam theMember = new ReferenceAndListParam();
//  //        ReferenceOrListParam memberRefParam = new ReferenceOrListParam();
//  //        ReferenceParam member = new ReferenceParam();
//  //        member.setValue(practitionerId);
//  //        memberRefParam.addOr(member);
//  //        theMember.addAnd(memberRefParam);
//  //        groupSearchParameterMap.add(MEMBER, theMember);
//  //        IBundleProvider groupsBundle = groupIFhirResourceDao.search(groupSearchParameterMap);
//  //        return groupsBundle != null
//  //                ? groupsBundle.getResources(0, groupsBundle.size())
//  //                : new ArrayList<>();
//  //    }
//  //
//  //    private String getLocationIdentifierValue(Identifier locationIdentifier) {
//  //        if (locationIdentifier.getUse() != null
//  //                && locationIdentifier.getUse().equals(Identifier.IdentifierUse.OFFICIAL)) {
//  //            return locationIdentifier.getValue();
//  //        }
//  //        return EMPTY_STRING;
//  //    }
//  //
//  //    private List<IBaseResource> getManagingOrganizationsOfCareTeams(List<CareTeam>
//  // careTeamsList) {
//  //        List<String> organizationIdReferences = new ArrayList<>();
//  //        List<Reference> managingOrganizations = new ArrayList<>();
//  //        for (CareTeam careTeam : careTeamsList) {
//  //            if (careTeam.hasManagingOrganization()) {
//  //                managingOrganizations.addAll(careTeam.getManagingOrganization());
//  //            }
//  //        }
//  //        for (Reference managingOrganization : managingOrganizations) {
//  //            if (managingOrganization != null && managingOrganization.getReference() != null) {
//  //                organizationIdReferences.add(managingOrganization.getReference());
//  //            }
//  //        }
//  //        return searchOrganizationsById(organizationIdReferences);
//  //    }
//  //
//  //    public IFhirResourceDao<Practitioner> getPractitionerIFhirResourceDao() {
//  //        return practitionerIFhirResourceDao;
//  //    }
//  //
//  //    public void setPractitionerIFhirResourceDao(
//  //            IFhirResourceDao<Practitioner> practitionerIFhirResourceDao) {
//  //        this.practitionerIFhirResourceDao = practitionerIFhirResourceDao;
//  //    }
//  //
//  //    public IFhirResourceDao<PractitionerRole> getPractitionerRoleIFhirResourceDao() {
//  //        return practitionerRoleIFhirResourceDao;
//  //    }
//  //
//  //    public void setPractitionerRoleIFhirResourceDao(
//  //            IFhirResourceDao<PractitionerRole> practitionerRoleIFhirResourceDao) {
//  //        this.practitionerRoleIFhirResourceDao = practitionerRoleIFhirResourceDao;
//  //    }
//  //
//  //    public IFhirResourceDao<CareTeam> getCareTeamIFhirResourceDao() {
//  //        return careTeamIFhirResourceDao;
//  //    }
//  //
//  //    public void setCareTeamIFhirResourceDao(IFhirResourceDao<CareTeam>
// careTeamIFhirResourceDao)
//  // {
//  //        this.careTeamIFhirResourceDao = careTeamIFhirResourceDao;
//  //    }
//  //
//  //    public IFhirResourceDao<OrganizationAffiliation>
//  // getOrganizationAffiliationIFhirResourceDao() {
//  //        return organizationAffiliationIFhirResourceDao;
//  //    }
//  //
//  //    public void setOrganizationAffiliationIFhirResourceDao(
//  //            IFhirResourceDao<OrganizationAffiliation> organizationAffiliationIFhirResourceDao)
// {
//  //        this.organizationAffiliationIFhirResourceDao =
// organizationAffiliationIFhirResourceDao;
//  //    }
//  //
//  //    public IFhirResourceDao<Organization> getOrganizationIFhirResourceDao() {
//  //        return organizationIFhirResourceDao;
//  //    }
//  //
//  //    public void setOrganizationIFhirResourceDao(
//  //            IFhirResourceDao<Organization> organizationIFhirResourceDao) {
//  //        this.organizationIFhirResourceDao = organizationIFhirResourceDao;
//  //    }
//  //
//  //    public LocationHierarchyResourceProvider getLocationHierarchyResourceProvider() {
//  //        return locationHierarchyResourceProvider;
//  //    }
//  //
//  //    public void setLocationHierarchyResourceProvider(
//  //            LocationHierarchyResourceProvider locationHierarchyResourceProvider) {
//  //        this.locationHierarchyResourceProvider = locationHierarchyResourceProvider;
//  //    }
//  //
//  //    public IFhirResourceDao<Location> getLocationIFhirResourceDao() {
//  //        return locationIFhirResourceDao;
//  //    }
//  //
//  //    public void setLocationIFhirResourceDao(IFhirResourceDao<Location>
// locationIFhirResourceDao)
//  // {
//  //        this.locationIFhirResourceDao = locationIFhirResourceDao;
//  //    }
//  //
//  //    public IFhirResourceDao<Group> getGroupIFhirResourceDao() {
//  //        return groupIFhirResourceDao;
//  //    }
//  //
//  //    public void setGroupIFhirResourceDao(IFhirResourceDao<Group> groupIFhirResourceDao) {
//  //        this.groupIFhirResourceDao = groupIFhirResourceDao;
//  //    }
// }
