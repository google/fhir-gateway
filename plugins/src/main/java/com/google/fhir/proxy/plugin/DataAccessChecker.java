/*
 * Copyright 2021-2022 Google LLC
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
package com.google.fhir.proxy.plugin;

import static com.google.fhir.proxy.ProxyConstants.SYNC_STRATEGY;
import static org.hl7.fhir.r4.model.Claim.CARE_TEAM;
import static org.smartregister.utils.Constants.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.SpecialParam;
import ca.uhn.fhir.rest.param.TokenParam;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.JwtUtil;
import com.google.fhir.proxy.interfaces.*;
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

public class DataAccessChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(DataAccessChecker.class);
  private final String applicationId;
  private final List<String> careTeamIds;
  private final List<String> locationIds;
  private final List<String> organizationIds;

  private final List<String> syncStrategy;

  private static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

  private DataAccessChecker(
      String applicationId,
      List<String> careTeamIds,
      List<String> locationIds,
      List<String> organizationIds,
      List<String> syncStrategy) {
    this.applicationId = applicationId;
    this.careTeamIds = careTeamIds;
    this.organizationIds = organizationIds;
    this.locationIds = locationIds;
    this.syncStrategy = syncStrategy;
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

    switch (requestDetails.getRequestType()) {
      case GET:
      case POST:

      case PUT:

      case PATCH:

      default:
        // TODO handle other cases like DELETE
        return new OpenSRPSyncAccessDecision(
            applicationId, careTeamIds, locationIds, organizationIds, syncStrategy);
    }
  }

  @Named(value = "data")
  static class Factory implements AccessCheckerFactory {
    private static final String PROXY_TO_ENV = "PROXY_TO";

    private String getApplicationIdFromJWT(DecodedJWT jwt) {
      return JwtUtil.getClaimOrDie(jwt, FHIR_CORE_APPLICATION_ID_CLAIM);
    }

    private IGenericClient createFhirClientForR4() {
      String fhirServer = System.getenv(PROXY_TO_ENV);
      FhirContext ctx = FhirContext.forR4();
      IGenericClient client = ctx.newRestfulGenericClient(fhirServer);
      return client;
    }

    private Composition readCompositionResource(
        HttpFhirClient httpFhirClient, String applicationId) {
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

      // Adding isAuthProvided
      SpecialParam isAuthProvided = new SpecialParam();
      isAuthProvided.setValue("false");
      List<IQueryParameterType> l = new ArrayList<IQueryParameterType>();
      l.add(isAuthProvided);
      hmOut.put(PractitionerDetails.SP_IS_AUTH_PROVIDED, l);

      return hmOut;
    }

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      String applicationId = getApplicationIdFromJWT(jwt);
      Composition composition = readCompositionResource(httpFhirClient, applicationId);
      String binaryResourceReference = getBinaryResourceReference(composition);
      Binary binary = findApplicationConfigBinaryResource(binaryResourceReference);
      List<String> syncStrategy = findSyncStrategy(binary);
      PractitionerDetails practitionerDetails = readPractitionerDetails(jwt.getId());
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
            careTeamIds.add(careTeam.getId());
          }
        } else if (syncStrategy.contains(ORGANIZATION)) {
          organizations =
              practitionerDetails != null
                      && practitionerDetails.getFhirPractitionerDetails() != null
                  ? practitionerDetails.getFhirPractitionerDetails().getOrganizations()
                  : Collections.singletonList(new Organization());
          for (Organization organization : organizations) {
            organizationIds.add(organization.getId());
          }
        } else if (syncStrategy.contains(LOCATION)) {
          locations =
              practitionerDetails != null
                      && practitionerDetails.getFhirPractitionerDetails() != null
                  ? practitionerDetails.getFhirPractitionerDetails().getLocations()
                  : Collections.singletonList(new Location());
          for (Location location : locations) {
            locationIds.add(location.getId());
          }
        }
      }

      return new DataAccessChecker(
          applicationId, careTeamIds, locationIds, organizationIds, syncStrategy);
    }
  }
}
