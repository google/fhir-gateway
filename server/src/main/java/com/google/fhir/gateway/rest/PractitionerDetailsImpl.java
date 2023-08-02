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

import static com.google.fhir.gateway.util.Constants.*;
import static org.smartregister.utils.Constants.EMPTY_STRING;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import com.google.fhir.gateway.util.Constants;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;

public class PractitionerDetailsImpl {
  private FhirContext fhirR4Context = FhirContext.forR4();

  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

  private static final Bundle EMPTY_BUNDLE = new Bundle();

  private static final Logger logger = LoggerFactory.getLogger(PractitionerDetailsImpl.class);

  private IGenericClient r4FhirClient =
      fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));

  private IGenericClient getFhirClientForR4() {
    return r4FhirClient;
  }

  public PractitionerDetails getPractitionerDetails(String keycloakUUID) {
    Practitioner practitioner = getPractitionerByIdentifier(keycloakUUID);
    String practitionerId = EMPTY_STRING;
    if (practitioner.getIdElement() != null && practitioner.getIdElement().getIdPart() != null) {
      practitionerId = practitioner.getIdElement().getIdPart();
    }

    List<Bundle.BundleEntryComponent> managingOrganizationBundleEntryComponentList;
    List<Organization> managingOrganizations = new ArrayList<>();

    List<CareTeam> careTeams = new ArrayList<>();
    if (StringUtils.isNotBlank(practitionerId)) {
      logger.info("Searching for care teams for practitioner with id: " + practitionerId);
      Bundle careTeamBundle = getCareTeams(practitionerId);
      careTeams = mapBundleToCareTeams(careTeamBundle);
    }

    logger.info("Searching for Organizations tied with CareTeams: ");
    List<String> careTeamManagingOrganizationIds = getManagingOrganizationsOfCareTeamIds(careTeams);

    Bundle careTeamManagingOrganizations = getOrganizationsById(careTeamManagingOrganizationIds);
    logger.info("Managing Organization are fetched");

    List<Organization> managingOrganizationTeams =
        mapBundleToOrganizations(careTeamManagingOrganizations);

    logger.info("Searching for organizations of practitioner with id: " + practitioner);

    List<PractitionerRole> practitionerRoleList =
        getPractitionerRolesByPractitionerId(practitionerId);
    logger.info("Practitioner Roles are fetched");

    List<String> practitionerOrganizationIds =
        getOrganizationIdsByPractitionerRoles(practitionerRoleList);

    Bundle practitionerOrganizations = getOrganizationsById(practitionerOrganizationIds);

    List<Organization> teams = mapBundleToOrganizations(practitionerOrganizations);
    // TODO Fix Distinct
    List<Organization> bothOrganizations =
        Stream.concat(managingOrganizationTeams.stream(), teams.stream())
            .distinct()
            .collect(Collectors.toList());
    Bundle groupsBundle = getGroupsAssignedToPractitioner(practitionerId);
    logger.info("Groups are fetched");

    List<Group> groupsList = mapBundleToGroups(groupsBundle);

    logger.info("Searching for locations by organizations");

    Bundle organizationAffiliationsBundle =
        getOrganizationAffiliationsByOrganizationIdsBundle(
            Stream.concat(
                    careTeamManagingOrganizationIds.stream(), practitionerOrganizationIds.stream())
                .distinct()
                .collect(Collectors.toList()));

    List<OrganizationAffiliation> organizationAffiliations =
        mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);

    List<String> locationIds =
        getLocationIdentifiersByOrganizationAffiliations(organizationAffiliations);

    List<String> locationsIdentifiers =
        getOfficialLocationIdentifiersByLocationIds(
            locationIds); // TODO Investigate why the Location ID and official identifiers are
    // different

    logger.info("Searching for location hierarchy list by locations identifiers");
    //      List<LocationHierarchy> locationHierarchyList =
    //              getLocationsHierarchyByOfficialLocationIdentifiers(locationsIdentifiers);
    //      fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);

    logger.info("Searching for locations by ids");
    List<Location> locationsList = getLocationsByIds(locationIds);

    PractitionerDetails practitionerDetails = new PractitionerDetails();
    FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
    practitionerDetails.setId(practitionerId);
    fhirPractitionerDetails.setId(practitionerId);
    fhirPractitionerDetails.setCareTeams(careTeams);
    fhirPractitionerDetails.setPractitioners(Arrays.asList(practitioner));
    fhirPractitionerDetails.setGroups(groupsList);
    fhirPractitionerDetails.setLocations(locationsList);
    fhirPractitionerDetails.setLocationHierarchyList(Arrays.asList(new LocationHierarchy()));
    fhirPractitionerDetails.setPractitionerRoles(practitionerRoleList);
    fhirPractitionerDetails.setOrganizationAffiliations(organizationAffiliations);
    fhirPractitionerDetails.setOrganizations(bothOrganizations);

    practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
    return practitionerDetails;
  }

  private Practitioner getPractitionerByIdentifier(String identifier) {
    Bundle resultBundle =
        getFhirClientForR4()
            .search()
            .forResource(Practitioner.class)
            .where(Practitioner.IDENTIFIER.exactly().identifier(identifier))
            .returnBundle(Bundle.class)
            .execute();
    return resultBundle != null
        ? (Practitioner) resultBundle.getEntryFirstRep().getResource()
        : null;
  }

  public Bundle getCareTeams(String practitionerId) {
    logger.info("Searching for Care Teams with practitioner id :" + practitionerId);
    return getFhirClientForR4()
        .search()
        .forResource(CareTeam.class)
        .where(
            CareTeam.PARTICIPANT.hasId(
                Enumerations.ResourceType.PRACTITIONER.toCode()
                    + Constants.FORWARD_SLASH
                    + practitionerId))
        .returnBundle(Bundle.class)
        .execute();
  }

  private Bundle getPractitionerRoles(String practitionerId) {
    logger.info("Searching for Practitioner roles  with practitioner id :" + practitionerId);
    return getFhirClientForR4()
        .search()
        .forResource(PractitionerRole.class)
        .where(PractitionerRole.PRACTITIONER.hasId(practitionerId))
        .returnBundle(Bundle.class)
        .execute();
  }

  private static String getReferenceIDPart(String reference) {
    return reference.substring(reference.indexOf(Constants.FORWARD_SLASH) + 1);
  }

  private Bundle getOrganizationsById(List<String> organizationIds) {
    return organizationIds.isEmpty()
        ? EMPTY_BUNDLE
        : getFhirClientForR4()
            .search()
            .forResource(Organization.class)
            .where(new ReferenceClientParam(BaseResource.SP_RES_ID).hasAnyOfIds(organizationIds))
            .returnBundle(Bundle.class)
            .execute();
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

  private @Nullable List<String> getOfficialLocationIdentifiersByLocationIds(
      List<String> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<Location> locations = getLocationsByIds(locationIds);

    return locations.stream()
        .map(
            it ->
                it.getIdentifier().stream()
                    .filter(
                        id -> id.hasUse() && id.getUse().equals(Identifier.IdentifierUse.OFFICIAL))
                    .map(it2 -> it2.getValue())
                    .collect(Collectors.toList()))
        .flatMap(it3 -> it3.stream())
        .collect(Collectors.toList());
  }

  private List<String> getOrganizationAffiliationsByOrganizationIds(List<String> organizationIds) {
    if (organizationIds == null || organizationIds.isEmpty()) {
      return new ArrayList<>();
    }
    Bundle organizationAffiliationsBundle =
        getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);
    List<OrganizationAffiliation> organizationAffiliations =
        mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);
    return getLocationIdentifiersByOrganizationAffiliations(organizationAffiliations);
  }

  private Bundle getOrganizationAffiliationsByOrganizationIdsBundle(List<String> organizationIds) {
    return organizationIds.isEmpty()
        ? EMPTY_BUNDLE
        : getFhirClientForR4()
            .search()
            .forResource(OrganizationAffiliation.class)
            .where(OrganizationAffiliation.PRIMARY_ORGANIZATION.hasAnyOfIds(organizationIds))
            .returnBundle(Bundle.class)
            .execute();
  }

  private List<String> getLocationIdentifiersByOrganizationAffiliations(
      List<OrganizationAffiliation> organizationAffiliations) {

    return organizationAffiliations.stream()
        .map(
            organizationAffiliation ->
                getReferenceIDPart(
                    organizationAffiliation.getLocation().stream()
                        .findFirst()
                        .get()
                        .getReference()))
        .collect(Collectors.toList());
  }

  public List<CareTeam> mapBundleToCareTeams(Bundle careTeams) {
    return careTeams.getEntry().stream()
        .map(bundleEntryComponent -> (CareTeam) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<String> getManagingOrganizationsOfCareTeamIds(List<CareTeam> careTeamsList) {
    logger.info("Searching for Organizations with care teams list of size:" + careTeamsList.size());
    return careTeamsList.stream()
        .filter(careTeam -> careTeam.hasManagingOrganization())
        .flatMap(it -> it.getManagingOrganization().stream())
        .map(it -> getReferenceIDPart(it.getReference()))
        .collect(Collectors.toList());
  }

  private List<PractitionerRole> getPractitionerRolesByPractitionerId(String practitionerId) {
    Bundle practitionerRoles = getPractitionerRoles(practitionerId);
    return mapBundleToPractitionerRolesWithOrganization(practitionerRoles);
  }

  private List<String> getOrganizationIdsByPractitionerRoles(
      List<PractitionerRole> practitionerRoles) {
    return practitionerRoles.stream()
        .filter(practitionerRole -> practitionerRole.hasOrganization())
        .map(it -> getReferenceIDPart(it.getOrganization().getReference()))
        .collect(Collectors.toList());
  }

  private Bundle getGroupsAssignedToPractitioner(String practitionerId) {
    return getFhirClientForR4()
        .search()
        .forResource(Group.class)
        .where(Group.MEMBER.hasId(practitionerId))
        .where(Group.CODE.exactly().systemAndCode(HTTP_SNOMED_INFO_SCT, PRACTITIONER_GROUP_CODE))
        .returnBundle(Bundle.class)
        .execute();
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  private List<PractitionerRole> mapBundleToPractitionerRolesWithOrganization(
      Bundle practitionerRoles) {
    return practitionerRoles.getEntry().stream()
        .map(it -> (PractitionerRole) it.getResource())
        .collect(Collectors.toList());
  }

  private List<Group> mapBundleToGroups(Bundle groupsBundle) {
    return groupsBundle.getEntry().stream()
        .map(bundleEntryComponent -> (Group) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<Organization> mapBundleToOrganizations(Bundle organizationBundle) {
    return organizationBundle.getEntry().stream()
        .map(bundleEntryComponent -> (Organization) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<OrganizationAffiliation> mapBundleToOrganizationAffiliation(
      Bundle organizationAffiliationBundle) {
    return organizationAffiliationBundle.getEntry().stream()
        .map(bundleEntryComponent -> (OrganizationAffiliation) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }
}
