package com.google.fhir.gateway.plugin;

import static org.smartregister.utils.Constants.EMPTY_STRING;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.ParentChildrenMap;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;
import org.smartregister.utils.Constants;
import org.springframework.lang.Nullable;

public class OpenSRPHelper {
  private static final Logger logger = LoggerFactory.getLogger(OpenSRPHelper.class);
  public static final String PRACTITIONER_GROUP_CODE = "405623001";
  public static final String HTTP_SNOMED_INFO_SCT = "http://snomed.info/sct";
  public static final Bundle EMPTY_BUNDLE = new Bundle();
  private IGenericClient r4FHIRClient;

  public OpenSRPHelper(IGenericClient fhirClient) {
    this.r4FHIRClient = fhirClient;
  }

  private IGenericClient getFhirClientForR4() {
    return r4FHIRClient;
  }

  public PractitionerDetails getPractitionerDetailsByKeycloakId(String keycloakUUID) {
    PractitionerDetails practitionerDetails = new PractitionerDetails();

    logger.info("Searching for practitioner with identifier: " + keycloakUUID);
    IBaseResource practitioner = getPractitionerByIdentifier(keycloakUUID);
    String practitionerId = EMPTY_STRING;

    if (practitioner.getIdElement() != null && practitioner.getIdElement().getIdPart() != null) {
      practitionerId = practitioner.getIdElement().getIdPart();
    }

    if (StringUtils.isNotBlank(practitionerId)) {

      practitionerDetails = getPractitionerDetailsByPractitionerId(practitionerId);

    } else {
      logger.error("Practitioner with KC identifier: " + keycloakUUID + " not found");
      practitionerDetails.setId(Constants.PRACTITIONER_NOT_FOUND);
    }

    return practitionerDetails;
  }

  public Bundle getSupervisorPractitionerDetailsByKeycloakId(String keycloakUUID) {
    Bundle bundle = new Bundle();

    logger.info("Searching for practitioner with identifier: " + keycloakUUID);
    IBaseResource practitioner = getPractitionerByIdentifier(keycloakUUID);
    String practitionerId = EMPTY_STRING;

    if (practitioner.getIdElement() != null && practitioner.getIdElement().getIdPart() != null) {
      practitionerId = practitioner.getIdElement().getIdPart();
    }

    if (StringUtils.isNotBlank(practitionerId)) {

      bundle = getAttributedPractitionerDetailsByPractitionerId(practitionerId);

    } else {
      logger.error("Practitioner with KC identifier: " + keycloakUUID + " not found");
    }

    return bundle;
  }

  private Bundle getAttributedPractitionerDetailsByPractitionerId(String practitionerId) {
    Bundle responseBundle = new Bundle();
    List<String> attributedPractitionerIds = new ArrayList<>();
    PractitionerDetails practitionerDetails =
        getPractitionerDetailsByPractitionerId(practitionerId);

    List<CareTeam> careTeamList = practitionerDetails.getFhirPractitionerDetails().getCareTeams();
    // Get other guys.

    List<String> careTeamManagingOrganizationIds =
        getManagingOrganizationsOfCareTeamIds(careTeamList);
    List<String> supervisorCareTeamOrganizationLocationIds =
        getLocationIdentifiersByOrganizationIds(careTeamManagingOrganizationIds);
    List<String> officialLocationIds =
        getOfficialLocationIdentifiersByLocationIds(supervisorCareTeamOrganizationLocationIds);
    List<LocationHierarchy> locationHierarchies =
        getLocationsHierarchyByOfficialLocationIdentifiers(officialLocationIds);
    List<ParentChildrenMap> parentChildrenList =
        locationHierarchies.stream()
            .flatMap(
                locationHierarchy ->
                    locationHierarchy
                        .getLocationHierarchyTree()
                        .getLocationsHierarchy()
                        .getParentChildren()
                        .stream())
            .collect(Collectors.toList());
    List<String> attributedLocationsList =
        parentChildrenList.stream()
            .flatMap(parentChildren -> parentChildren.children().stream())
            .map(it -> it.getName())
            .collect(Collectors.toList());
    List<String> attributedOrganizationIds =
        getOrganizationIdsByLocationIds(attributedLocationsList);

    // Get care teams by organization Ids
    List<CareTeam> attributedCareTeams = getCareTeamsByOrganizationIds(attributedOrganizationIds);
    careTeamList.addAll(attributedCareTeams);

    for (CareTeam careTeam : careTeamList) {
      // Add current supervisor practitioners
      attributedPractitionerIds.addAll(
          careTeam.getParticipant().stream()
              .filter(
                  it ->
                      it.hasMember()
                          && it.getMember()
                              .getReference()
                              .startsWith(Enumerations.ResourceType.PRACTITIONER.toCode()))
              .map(it -> getReferenceIDPart(it.getMember().getReference()))
              .collect(Collectors.toList()));
    }

    List<Bundle.BundleEntryComponent> bundleEntryComponentList = new ArrayList<>();

    for (String attributedPractitionerId : attributedPractitionerIds) {
      bundleEntryComponentList.add(
          new Bundle.BundleEntryComponent()
              .setResource(getPractitionerDetailsByPractitionerId(attributedPractitionerId)));
    }

    responseBundle.setEntry(bundleEntryComponentList);
    responseBundle.setTotal(bundleEntryComponentList.size());
    return responseBundle;
  }

  private List<String> getOrganizationIdsByLocationIds(List<String> attributedLocationsList) {
    if (attributedLocationsList == null || attributedLocationsList.isEmpty()) {
      return new ArrayList<>();
    }

    Bundle organizationAffiliationsBundle =
        getFhirClientForR4()
            .search()
            .forResource(OrganizationAffiliation.class)
            .where(OrganizationAffiliation.LOCATION.hasAnyOfIds(attributedLocationsList))
            .returnBundle(Bundle.class)
            .execute();

    return organizationAffiliationsBundle.getEntry().stream()
        .map(
            bundleEntryComponent ->
                getReferenceIDPart(
                    ((OrganizationAffiliation) bundleEntryComponent.getResource())
                        .getOrganization()
                        .getReference()))
        .collect(Collectors.toList());
  }

  private PractitionerDetails getPractitionerDetailsByPractitionerId(String practitionerId) {

    PractitionerDetails practitionerDetails = new PractitionerDetails();
    FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();

    logger.info("Searching for care teams for practitioner with id: " + practitionerId);
    Bundle careTeams = getCareTeams(practitionerId);
    List<CareTeam> careTeamsList = mapBundleToCareTeams(careTeams);
    fhirPractitionerDetails.setCareTeams(careTeamsList);

    StringType practitionerIdString = new StringType(practitionerId);
    fhirPractitionerDetails.setPractitionerId(practitionerIdString);

    logger.info("Searching for Organizations tied with CareTeams: ");
    List<String> careTeamManagingOrganizationIds =
        getManagingOrganizationsOfCareTeamIds(careTeamsList);

    Bundle careTeamManagingOrganizations = getOrganizationsById(careTeamManagingOrganizationIds);
    logger.info("Managing Organization are fetched");

    List<Organization> managingOrganizationTeams =
        mapBundleToOrganizations(careTeamManagingOrganizations);

    logger.info("Searching for organizations of practitioner with id: " + practitionerId);

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

    fhirPractitionerDetails.setOrganizations(bothOrganizations);

    fhirPractitionerDetails.setPractitionerRoles(practitionerRoleList);

    Bundle groupsBundle = getGroupsAssignedToPractitioner(practitionerId);
    logger.info("Groups are fetched");

    List<Group> groupsList = mapBundleToGroups(groupsBundle);
    fhirPractitionerDetails.setGroups(groupsList);
    fhirPractitionerDetails.setId(practitionerIdString.getValue());

    logger.info("Searching for locations by organizations");

    List<String> locationIds =
        getLocationIdentifiersByOrganizationIds(
            Stream.concat(
                    careTeamManagingOrganizationIds.stream(), practitionerOrganizationIds.stream())
                .distinct()
                .collect(Collectors.toList()));

    List<String> locationsIdentifiers =
        getOfficialLocationIdentifiersByLocationIds(
            locationIds); // TODO Investigate why the Location ID and official identifiers are
    // different

    logger.info("Searching for location hierarchy list by locations identifiers");
    List<LocationHierarchy> locationHierarchyList =
        getLocationsHierarchyByOfficialLocationIdentifiers(locationsIdentifiers);
    fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);

    logger.info("Searching for locations by ids");
    List<Location> locationsList = getLocationsByIds(locationIds);
    fhirPractitionerDetails.setLocations(locationsList);

    practitionerDetails.setId(practitionerId);
    practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);

    return practitionerDetails;
  }

  private List<Organization> mapBundleToOrganizations(Bundle organizationBundle) {
    return organizationBundle.getEntry().stream()
        .map(bundleEntryComponent -> (Organization) bundleEntryComponent.getResource())
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

  private List<CareTeam> getCareTeamsByOrganizationIds(List<String> organizationIds) {
    if (organizationIds.isEmpty()) return new ArrayList<>();

    Bundle bundle =
        getFhirClientForR4()
            .search()
            .forResource(CareTeam.class)
            .where(
                CareTeam.PARTICIPANT.hasAnyOfIds(
                    organizationIds.stream()
                        .map(
                            it ->
                                Enumerations.ResourceType.ORGANIZATION.toCode()
                                    + Constants.FORWARD_SLASH
                                    + it)
                        .collect(Collectors.toList())))
            .returnBundle(Bundle.class)
            .execute();

    return bundle.getEntry().stream()
        .filter(it -> ((CareTeam) it.getResource()).hasManagingOrganization())
        .map(it -> ((CareTeam) it.getResource()))
        .collect(Collectors.toList());
  }

  private Bundle getCareTeams(String practitionerId) {
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

  private String getReferenceIDPart(String reference) {
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

  private List<String> getLocationIdentifiersByOrganizationIds(List<String> organizationIds) {
    if (organizationIds == null || organizationIds.isEmpty()) {
      return new ArrayList<>();
    }

    Bundle locationsBundle =
        getFhirClientForR4()
            .search()
            .forResource(OrganizationAffiliation.class)
            .where(OrganizationAffiliation.PRIMARY_ORGANIZATION.hasAnyOfIds(organizationIds))
            .returnBundle(Bundle.class)
            .execute();

    return locationsBundle.getEntry().stream()
        .map(
            bundleEntryComponent ->
                getReferenceIDPart(
                    ((OrganizationAffiliation) bundleEntryComponent.getResource())
                        .getLocation().stream().findFirst().get().getReference()))
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

  private List<CareTeam> mapBundleToCareTeams(Bundle careTeams) {
    return careTeams.getEntry().stream()
        .map(bundleEntryComponent -> (CareTeam) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<PractitionerRole> mapBundleToPractitionerRolesWithOrganization(
      Bundle practitionerRoles) {
    return practitionerRoles.getEntry().stream()
        .filter(
            bundleEntryComponent ->
                ((PractitionerRole) bundleEntryComponent.getResource()).hasOrganization())
        .map(it -> (PractitionerRole) it.getResource())
        .collect(Collectors.toList());
  }

  private List<Group> mapBundleToGroups(Bundle groupsBundle) {
    return groupsBundle.getEntry().stream()
        .map(bundleEntryComponent -> (Group) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<LocationHierarchy> getLocationsHierarchyByOfficialLocationIdentifiers(
      List<String> officialLocationIdentifiers) {
    if (officialLocationIdentifiers.isEmpty()) return new ArrayList<>();

    Bundle bundle =
        getFhirClientForR4()
            .search()
            .forResource(LocationHierarchy.class)
            .where(LocationHierarchy.IDENTIFIER.exactly().codes(officialLocationIdentifiers))
            .returnBundle(Bundle.class)
            .execute();

    return bundle.getEntry().stream()
        .map(it -> ((LocationHierarchy) it.getResource()))
        .collect(Collectors.toList());
  }
}
