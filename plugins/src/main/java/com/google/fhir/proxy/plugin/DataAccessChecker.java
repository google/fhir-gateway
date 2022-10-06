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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.JwtUtil;
import com.google.fhir.proxy.interfaces.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.practitioner.PractitionerDetails;

import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.fhir.proxy.ProxyConstants.REALM_ACCESS;
import static com.google.fhir.proxy.ProxyConstants.SYNC_STRATEGY;
import static org.smartregister.utils.Constants.*;

public class DataAccessChecker implements AccessChecker {

    private static final Logger logger = LoggerFactory.getLogger(DataAccessChecker.class);
    private final String applicationId;
    private final List<String> careTeamIds;
    private final List<String> locationIds;
    private final List<String> organizationIds;

    private final List<String> syncStrategy;

    private static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

    private DataAccessChecker(String applicationId, List<String> careTeamIds, List<String> locationIds, List<String> organizationIds, List<String> syncStrategy) {
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
                return new OpenSRPSyncAccessDecision(applicationId, careTeamIds, locationIds, organizationIds, syncStrategy);
        }
    }

    @Named(value = "data")
    static class Factory implements AccessCheckerFactory {
        private static final String PROXY_TO_ENV = "PROXY_TO";


        private String getApplicationIdFromJWT(DecodedJWT jwt) {
            Claim claim = jwt.getClaim(REALM_ACCESS);
            String applicationId = JwtUtil.getClaimOrDie(jwt, FHIR_CORE_APPLICATION_ID_CLAIM);
            return applicationId;
        }

        private IGenericClient createFhirClientForR4() {
            String fhirServer = System.getenv(PROXY_TO_ENV);
            FhirContext ctx = FhirContext.forR4();
            IGenericClient client = ctx.newRestfulGenericClient(fhirServer);
            return client;
        }

        private Composition readCompositionResource(HttpFhirClient httpFhirClient, String applicationId) {
            IGenericClient client = createFhirClientForR4();
            Bundle compositionBundle = client.search().forResource(Composition.class).where(Composition.IDENTIFIER.exactly().identifier(applicationId)).returnBundle(Bundle.class)
                    .execute();
            List<Bundle.BundleEntryComponent> compositionEntries = compositionBundle.getEntry();
            Bundle.BundleEntryComponent compositionEntry = compositionEntries.get(0);
            Composition composition = (Composition) compositionEntry.getResource();
            return composition;

        }

        private String getBinaryResourceReference(Composition composition) {
            List<Integer> indexes = composition.getSection().stream().
                    filter(v -> v.getFocus().getIdentifier() != null).
                    filter(v -> v.getFocus().getIdentifier().getValue() != null).
                    filter(v -> v.getFocus().getIdentifier().getValue().equals("application")).map(v -> composition.getSection().indexOf(v))
                    .collect(Collectors.toList());
            String id = composition.getSection().get(indexes.get(0)).getFocus().getReference();
            return id;
        }


        private Binary findApplicationConfigBinaryResource(String binaryResourceId) {
            IGenericClient client = createFhirClientForR4();
            Binary binary = client.read()
                    .resource(Binary.class)
                    .withId(binaryResourceId)
                    .execute();
            return binary;
        }

        private List<String> findSyncStrategy(Binary binary) {
            byte[] bytes = Base64.getDecoder().decode(binary.getDataElement().getValueAsString());
            String json = new String(bytes);
            JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
            JsonArray jsonArray = jsonObject.getAsJsonArray(SYNC_STRATEGY);
            List<String> syncStrategy = new ArrayList<>();
            if (jsonArray != null) {
                for (JsonElement jsonElement : jsonArray) {
                    syncStrategy.add(jsonElement.getAsString());
                }
            }
            return syncStrategy;
        }

        private PractitionerDetails readPractitionerDetails(String keycloakUUID) {
            IGenericClient client = createFhirClientForR4();
            Bundle practitionerDetailsBundle = client.search()
                    .forResource(PractitionerDetails.class)
                    .where(PractitionerDetails.KEYCLOAK_UUID.exactly().identifier(keycloakUUID))
                    .returnBundle(Bundle.class)
                    .execute();

            List<Bundle.BundleEntryComponent> practitionerDetailsBundleEntry = practitionerDetailsBundle.getEntry();
            Bundle.BundleEntryComponent practitionerDetailEntry = practitionerDetailsBundleEntry.get(0);
            PractitionerDetails practitionerDetails = (PractitionerDetails) practitionerDetailEntry.getResource();
            return practitionerDetails;
        }


        @Override
        public AccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient, FhirContext fhirContext, PatientFinder patientFinder) {
            String applicationId = getApplicationIdFromJWT(jwt);
            Composition composition = readCompositionResource(httpFhirClient, applicationId);
            String binaryResourceReference = getBinaryResourceReference(composition);
            Binary binary = findApplicationConfigBinaryResource(binaryResourceReference);
            List<String> syncStrategy = findSyncStrategy(binary);
            PractitionerDetails practitionerDetails = readPractitionerDetails(jwt.getId());
            List<CareTeam> careTeams = new ArrayList<>();
            List<Organization> organizations = new ArrayList<>();
            List<Location> locations = new ArrayList<>();
            List<String> careTeamIds = new ArrayList<>();
            List<String> organizationIds = new ArrayList<>();
            List<String> locationIds = new ArrayList<>();
            if (syncStrategy.contains(CARE_TEAM)) {
                careTeams = practitionerDetails.getFhirPractitionerDetails().getCareTeams();
                for (CareTeam careTeam : careTeams) {
                    careTeamIds.add(careTeam.getId());
                }
            } else if (syncStrategy.contains(ORGANIZATION)) {
                organizations = practitionerDetails.getFhirPractitionerDetails().getOrganizations();
                for (Organization organization : organizations) {
                    organizationIds.add(organization.getId());
                }
            } else if (syncStrategy.contains(LOCATION)) {
                locations = practitionerDetails.getFhirPractitionerDetails().getLocations();
                for (Location location : locations) {
                    locationIds.add(location.getId());
                }
            } else {

            }


            return new DataAccessChecker(applicationId, careTeamIds, locationIds, organizationIds, syncStrategy);
        }
    }
}
