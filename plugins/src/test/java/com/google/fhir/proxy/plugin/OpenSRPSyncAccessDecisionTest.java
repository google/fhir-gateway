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
package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.fhir.proxy.ProxyConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OpenSRPSyncAccessDecisionTest {

  private List<String> locationIds = new ArrayList<>();

  private List<String> careTeamIds = new ArrayList<>();

  private List<String> organisationIds = new ArrayList<>();

  private OpenSRPSyncAccessDecision testInstance;

  @Test
  @Ignore
  public void preprocessShouldAddAllFiltersWhenIdsForLocationsOrganisationsAndCareTeamsAreProvided()
      throws IOException {

    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    // Call the method under testing
    testInstance.preProcess(requestDetails);

    List<String> allIds = new ArrayList<>();
    allIds.addAll(locationIds);
    allIds.addAll(organisationIds);
    allIds.addAll(careTeamIds);

    for (String locationId : locationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.LOCATION_TAG_URL + "|" + locationId));
    }

    for (String careTeamId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(careTeamId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(careTeamId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.CARE_TEAM_TAG_URL + "|" + careTeamId));
    }

    for (String organisationId : organisationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(organisationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(organisationId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.ORGANISATION_TAG_URL + "|" + organisationId));
    }
  }

  @Test
  @Ignore
  public void preProcessShouldAddLocationIdFiltersWhenUserIsAssignedToLocationsOnly()
      throws IOException {
    locationIds.add("locationid12");
    locationIds.add("locationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    testInstance.preProcess(requestDetails);

    for (String locationId : locationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.LOCATION_TAG_URL + "|" + locationId));
    }

    for (String param : requestDetails.getParameters().get("_tag")) {
      Assert.assertFalse(param.contains(ProxyConstants.CARE_TEAM_TAG_URL));
      Assert.assertFalse(param.contains(ProxyConstants.ORGANISATION_TAG_URL));
    }
  }

  @Test
  @Ignore
  public void preProcessShouldAddCareTeamIdFiltersWhenUserIsAssignedToCareTeamsOnly()
      throws IOException {
    careTeamIds.add("careteamid1");
    careTeamIds.add("careteamid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    testInstance.preProcess(requestDetails);

    for (String locationId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.CARE_TEAM_TAG_URL + "|" + locationId));
    }

    for (String param : requestDetails.getParameters().get("_tag")) {
      Assert.assertFalse(param.contains(ProxyConstants.LOCATION_TAG_URL));
      Assert.assertFalse(param.contains(ProxyConstants.ORGANISATION_TAG_URL));
    }
  }

  @Test
  public void preProcessShouldAddOrganisationIdFiltersWhenUserIsAssignedToOrganisationsOnly()
      throws IOException {
    organisationIds.add("organizationid1");
    organisationIds.add("organizationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    ServletRequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    testInstance.preProcess(requestDetails);

    for (String locationId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          Arrays.asList(requestDetails.getParameters().get("_tag"))
              .contains(ProxyConstants.ORGANISATION_TAG_URL + "|" + locationId));
    }

    for (String param : requestDetails.getParameters().get("_tag")) {
      Assert.assertFalse(param.contains(ProxyConstants.LOCATION_TAG_URL));
      Assert.assertFalse(param.contains(ProxyConstants.CARE_TEAM_TAG_URL));
    }
  }

  private OpenSRPSyncAccessDecision createOpenSRPSyncAccessDecisionTestInstance() {
    return new OpenSRPSyncAccessDecision(
        "sample-application-id", true, locationIds, careTeamIds, organisationIds, null);
  }
}
