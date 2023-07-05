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
package com.google.fhir.gateway.plugin;

import static com.google.fhir.gateway.ProxyConstants.SYNC_STRATEGY;
import static org.hl7.fhir.r4.model.Claim.CARE_TEAM;
import static org.smartregister.utils.Constants.LOCATION;
import static org.smartregister.utils.Constants.ORGANIZATION;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.gateway.*;
import com.google.fhir.gateway.interfaces.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.practitioner.PractitionerDetails;

public class PermissionAccessChecker implements AccessChecker {
  private static final Logger logger = LoggerFactory.getLogger(PermissionAccessChecker.class);
  private final ResourceFinder resourceFinder;
  private final List<String> userRoles;
  private final String applicationId;

  private final List<String> careTeamIds;

  private final List<String> locationIds;

  private final List<String> organizationIds;

  private final List<String> syncStrategy;

  private PermissionAccessChecker(
      List<String> userRoles,
      ResourceFinderImp resourceFinder,
      String applicationId,
      List<String> careTeamIds,
      List<String> locationIds,
      List<String> organizationIds,
      List<String> syncStrategy) {
    Preconditions.checkNotNull(userRoles);
    Preconditions.checkNotNull(resourceFinder);
    Preconditions.checkNotNull(applicationId);
    Preconditions.checkNotNull(careTeamIds);
    Preconditions.checkNotNull(organizationIds);
    Preconditions.checkNotNull(locationIds);
    Preconditions.checkNotNull(syncStrategy);
    this.resourceFinder = resourceFinder;
    this.userRoles = userRoles;
    this.applicationId = applicationId;
    this.careTeamIds = careTeamIds;
    this.organizationIds = organizationIds;
    this.locationIds = locationIds;
    this.syncStrategy = syncStrategy;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      return processBundle(requestDetails);

    } else {

      boolean userHasRole =
          checkUserHasRole(
              requestDetails.getResourceName(), requestDetails.getRequestType().name());

      RequestTypeEnum requestType = requestDetails.getRequestType();

      switch (requestType) {
        case GET:
          return processGet(userHasRole);
        case DELETE:
          return processDelete(userHasRole);
        case POST:
          return processPost(userHasRole);
        case PUT:
          return processPut(userHasRole);
        default:
          // TODO handle other cases like PATCH
          return NoOpAccessDecision.accessDenied();
      }
    }
  }

  private boolean checkUserHasRole(String resourceName, String requestType) {
    return checkIfRoleExists(getAdminRoleName(resourceName), this.userRoles)
        || checkIfRoleExists(getRelevantRoleName(resourceName, requestType), this.userRoles);
  }

  private AccessDecision processGet(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processDelete(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision getAccessDecision(boolean userHasRole) {
    return userHasRole
        ? new OpenSRPSyncAccessDecision(
            applicationId, true, locationIds, careTeamIds, organizationIds, syncStrategy)
        : NoOpAccessDecision.accessDenied();
  }

  private AccessDecision processPost(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processPut(boolean userHasRole) {
    return getAccessDecision(userHasRole);
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) {
    boolean hasMissingRole = false;
    List<BundleResources> resourcesInBundle = resourceFinder.findResourcesInBundle(requestDetails);
    // Verify Authorization for individual requests in Bundle
    for (BundleResources bundleResources : resourcesInBundle) {
      if (!checkUserHasRole(
          bundleResources.getResource().fhirType(), bundleResources.getRequestType().name())) {

        if (isDevMode()) {
          hasMissingRole = true;
          logger.info(
              "Missing role "
                  + getRelevantRoleName(
                      bundleResources.getResource().fhirType(),
                      bundleResources.getRequestType().name()));
        } else {
          return NoOpAccessDecision.accessDenied();
        }
      }
    }

    return (isDevMode() && !hasMissingRole) || !isDevMode()
        ? NoOpAccessDecision.accessGranted()
        : NoOpAccessDecision.accessDenied();
  }

  private String getRelevantRoleName(String resourceName, String methodType) {
    return methodType + "_" + resourceName.toUpperCase();
  }

  private String getAdminRoleName(String resourceName) {
    return "MANAGE_" + resourceName.toUpperCase();
  }

  @VisibleForTesting
  protected boolean isDevMode() {
    return FhirProxyServer.isDevMode();
  }

  private boolean checkIfRoleExists(String roleName, List<String> existingRoles) {
    return existingRoles.contains(roleName);
  }

  @Named(value = "permission")
  static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String REALM_ACCESS_CLAIM = "realm_access";
    @VisibleForTesting static final String ROLES = "roles";

    @VisibleForTesting static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

    @VisibleForTesting static final String PROXY_TO_ENV = "PROXY_TO";

    private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
      Claim claim = jwt.getClaim(REALM_ACCESS_CLAIM);
      Map<String, Object> roles = claim.asMap();
      List<String> rolesList = (List) roles.get(ROLES);
      return rolesList;
    }

    private String getApplicationIdFromJWT(DecodedJWT jwt) {
      return JwtUtil.getClaimOrDie(jwt, FHIR_CORE_APPLICATION_ID_CLAIM);
    }

    private IGenericClient createFhirClientForR4() {
      String fhirServer = System.getenv(PROXY_TO_ENV);
      FhirContext ctx = FhirContext.forR4();
      IGenericClient client = ctx.newRestfulGenericClient(fhirServer);
      return client;
    }

    private Composition readCompositionResource(String applicationId) {
      IGenericClient client = createFhirClientForR4();
      Bundle compositionBundle =
          client
              .search()
              .forResource(Composition.class)
              .where(Composition.IDENTIFIER.exactly().identifier(applicationId))
              .returnBundle(Bundle.class)
              .execute();
      List<Bundle.BundleEntryComponent> compositionEntries =
          compositionBundle != null
              ? compositionBundle.getEntry()
              : Collections.singletonList(new Bundle.BundleEntryComponent());
      Bundle.BundleEntryComponent compositionEntry =
          compositionEntries.size() > 0 ? compositionEntries.get(0) : null;
      return compositionEntry != null ? (Composition) compositionEntry.getResource() : null;
    }

    private String getBinaryResourceReference(Composition composition) {
      List<Integer> indexes = new ArrayList<>();
      String id = "";
      if (composition != null && composition.getSection() != null) {
        indexes =
            composition.getSection().stream()
                .filter(v -> v.getFocus().getIdentifier() != null)
                .filter(v -> v.getFocus().getIdentifier().getValue() != null)
                .filter(v -> v.getFocus().getIdentifier().getValue().equals("application"))
                .map(v -> composition.getSection().indexOf(v))
                .collect(Collectors.toList());
        Composition.SectionComponent sectionComponent = composition.getSection().get(0);
        Reference focus = sectionComponent != null ? sectionComponent.getFocus() : null;
        id = focus != null ? focus.getReference() : null;
      }
      return id;
    }

    private Binary findApplicationConfigBinaryResource(String binaryResourceId) {
      IGenericClient client = createFhirClientForR4();
      Binary binary = null;
      if (!binaryResourceId.isBlank()) {
        binary = client.read().resource(Binary.class).withId(binaryResourceId).execute();
      }
      return binary;
    }

    private List<String> findSyncStrategy(Binary binary) {
      byte[] bytes =
          binary != null && binary.getDataElement() != null
              ? Base64.getDecoder().decode(binary.getDataElement().getValueAsString())
              : null;
      List<String> syncStrategy = new ArrayList<>();
      if (bytes != null) {
        String json = new String(bytes);
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
        JsonArray jsonArray = jsonObject.getAsJsonArray(SYNC_STRATEGY);
        if (jsonArray != null) {
          for (JsonElement jsonElement : jsonArray) {
            syncStrategy.add(jsonElement.getAsString());
          }
        }
      }
      return syncStrategy;
    }

    private PractitionerDetails readPractitionerDetails(String keycloakUUID) {
      IGenericClient client = createFhirClientForR4();
      //            Map<>
      Bundle practitionerDetailsBundle =
          client
              .search()
              .forResource(PractitionerDetails.class)
              .where(getMapForWhere(keycloakUUID))
              .returnBundle(Bundle.class)
              .execute();

      List<Bundle.BundleEntryComponent> practitionerDetailsBundleEntry =
          practitionerDetailsBundle.getEntry();
      Bundle.BundleEntryComponent practitionerDetailEntry =
          practitionerDetailsBundleEntry != null && practitionerDetailsBundleEntry.size() > 0
              ? practitionerDetailsBundleEntry.get(0)
              : null;
      return practitionerDetailEntry != null
          ? (PractitionerDetails) practitionerDetailEntry.getResource()
          : null;
    }

    public Map<String, List<IQueryParameterType>> getMapForWhere(String keycloakUUID) {
      Map<String, List<IQueryParameterType>> hmOut = new HashMap<>();
      // Adding keycloak-uuid
      TokenParam tokenParam = new TokenParam("keycloak-uuid");
      tokenParam.setValue(keycloakUUID);
      List<IQueryParameterType> lst = new ArrayList<IQueryParameterType>();
      lst.add(tokenParam);
      hmOut.put(PractitionerDetails.SP_KEYCLOAK_UUID, lst);

      return hmOut;
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder)
        throws AuthenticationException {
      List<String> userRoles = getUserRolesFromJWT(jwt);
      String applicationId = getApplicationIdFromJWT(jwt);
      Composition composition = readCompositionResource(applicationId);
      String binaryResourceReference = getBinaryResourceReference(composition);
      Binary binary = findApplicationConfigBinaryResource(binaryResourceReference);
      List<String> syncStrategy = findSyncStrategy(binary);
      PractitionerDetails practitionerDetails = readPractitionerDetails(jwt.getSubject());
      List<CareTeam> careTeams;
      List<Organization> organizations;
      List<Location> locations;
      List<String> careTeamIds = new ArrayList<>();
      List<String> organizationIds = new ArrayList<>();
      List<String> locationIds = new ArrayList<>();
      if (syncStrategy.size() > 0) {
        if (syncStrategy.contains(CARE_TEAM)) {
          careTeams =
              practitionerDetails != null
                      && practitionerDetails.getFhirPractitionerDetails() != null
                  ? practitionerDetails.getFhirPractitionerDetails().getCareTeams()
                  : Collections.singletonList(new CareTeam());
          for (CareTeam careTeam : careTeams) {
            if (careTeam.getIdElement() != null
                && careTeam.getIdElement().getIdPart() != null) {
              careTeamIds.add(careTeam.getIdElement().getIdPart());
            }
            careTeamIds.add(careTeam.getId());
          }
        } else if (syncStrategy.contains(ORGANIZATION)) {
          organizations =
              practitionerDetails != null
                      && practitionerDetails.getFhirPractitionerDetails() != null
                  ? practitionerDetails.getFhirPractitionerDetails().getOrganizations()
                  : Collections.singletonList(new Organization());
          for (Organization organization : organizations) {
            if (organization.getIdElement() != null
                && organization.getIdElement().getIdPart() != null) {
              organizationIds.add(organization.getIdElement().getIdPart());
            }
          }
        } else if (syncStrategy.contains(LOCATION)) {
          locations =
              practitionerDetails != null
                      && practitionerDetails.getFhirPractitionerDetails() != null
                  ? practitionerDetails.getFhirPractitionerDetails().getLocations()
                  : Collections.singletonList(new Location());
          for (Location location : locations) {
            if (location.getIdElement() != null
                && location.getIdElement().getIdPart() != null) {
              locationIds.add(location.getIdElement().getIdPart());
            }
          }
        }
      }
      return new PermissionAccessChecker(
          userRoles,
          ResourceFinderImp.getInstance(fhirContext),
          applicationId,
          careTeamIds,
          locationIds,
          organizationIds,
          syncStrategy);
    }
  }
}
