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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ITransaction;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.fhir.gateway.ProxyConstants;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OpenSRPSyncAccessDecisionTest {

  private List<String> locationIds = new ArrayList<>();

  private List<String> careTeamIds = new ArrayList<>();

  private List<String> organisationIds = new ArrayList<>();

  private List<String> userRoles = new ArrayList<>();

  private OpenSRPSyncAccessDecision testInstance;

  @Test
  public void
      preprocessShouldAddAllFiltersWhenIdsForLocationsOrganisationsAndCareTeamsAreProvided() {
    locationIds.addAll(Arrays.asList("my-location-id", "my-location-id2"));
    careTeamIds.add("my-careteam-id");
    organisationIds.add("my-organization-id");

    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    // Call the method under testing
    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    List<String> allIds = new ArrayList<>();
    allIds.addAll(locationIds);
    allIds.addAll(organisationIds);
    allIds.addAll(careTeamIds);

    for (String locationId : locationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.LOCATION_TAG_URL + "|" + locationId));
    }

    for (String careTeamId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(careTeamId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(careTeamId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.CARE_TEAM_TAG_URL + "|" + careTeamId));
    }

    for (String organisationId : organisationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(organisationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(organisationId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.ORGANISATION_TAG_URL + "|" + organisationId));
    }
  }

  @Test
  public void preProcessShouldAddLocationIdFiltersWhenUserIsAssignedToLocationsOnly()
      throws IOException {
    locationIds.add("locationid12");
    locationIds.add("locationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    for (String locationId : locationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.LOCATION_TAG_URL + "|" + locationId));
    }

    for (String param : mutatedRequest.getQueryParams().get("_tag")) {
      Assert.assertFalse(param.contains(ProxyConstants.CARE_TEAM_TAG_URL));
      Assert.assertFalse(param.contains(ProxyConstants.ORGANISATION_TAG_URL));
    }
  }

  @Test
  public void preProcessShouldAddCareTeamIdFiltersWhenUserIsAssignedToCareTeamsOnly()
      throws IOException {
    careTeamIds.add("careteamid1");
    careTeamIds.add("careteamid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    for (String locationId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.CARE_TEAM_TAG_URL + "|" + locationId));
    }

    for (String param : mutatedRequest.getQueryParams().get("_tag")) {
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

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    for (String locationId : careTeamIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.ORGANISATION_TAG_URL + "|" + locationId));
    }

    for (String param : mutatedRequest.getQueryParams().get("_tag")) {
      Assert.assertFalse(param.contains(ProxyConstants.LOCATION_TAG_URL));
      Assert.assertFalse(param.contains(ProxyConstants.CARE_TEAM_TAG_URL));
    }
  }

  @Test
  public void preProcessShouldAddFiltersWhenResourceNotInSyncFilterIgnoredResourcesFile() {
    organisationIds.add("organizationid1");
    organisationIds.add("organizationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
    requestDetails.setRequestPath("Patient");

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    for (String locationId : organisationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertEquals(1, mutatedRequest.getQueryParams().size());
      Assert.assertTrue(
          mutatedRequest
              .getQueryParams()
              .get("_tag")
              .contains(ProxyConstants.ORGANISATION_TAG_URL + "|" + locationId));
    }
  }

  @Test
  public void preProcessShouldSkipAddingFiltersWhenResourceInSyncFilterIgnoredResourcesFile() {
    organisationIds.add("organizationid1");
    organisationIds.add("organizationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Questionnaire");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Questionnaire");
    requestDetails.setRequestPath("Questionnaire");

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    for (String locationId : organisationIds) {
      Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
      Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
      Assert.assertNull(mutatedRequest);
    }
  }

  @Test
  public void
      preProcessShouldSkipAddingFiltersWhenSearchResourceByIdsInSyncFilterIgnoredResourcesFile() {
    organisationIds.add("organizationid1");
    organisationIds.add("organizationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("StructureMap");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    List<String> queryStringParamValues = Arrays.asList("1000", "2000", "3000");
    requestDetails.setCompleteUrl(
        "https://smartregister.org/fhir/StructureMap?_id="
            + StringUtils.join(queryStringParamValues, ","));
    Assert.assertEquals(
        "https://smartregister.org/fhir/StructureMap?_id=1000,2000,3000",
        requestDetails.getCompleteUrl());
    requestDetails.setRequestPath("StructureMap");

    Map<String, String[]> params = Maps.newHashMap();
    params.put("_id", new String[] {StringUtils.join(queryStringParamValues, ",")});
    requestDetails.setParameters(params);

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    Assert.assertNull(mutatedRequest);
  }

  @Test
  public void
      preProcessShouldAddFiltersWhenSearchResourceByIdsDoNotMatchSyncFilterIgnoredResources() {
    organisationIds.add("organizationid1");
    organisationIds.add("organizationid2");
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("StructureMap");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    List<String> queryStringParamValues = Arrays.asList("1000", "2000");
    requestDetails.setCompleteUrl(
        "https://smartregister.org/fhir/StructureMap?_id="
            + StringUtils.join(queryStringParamValues, ","));
    Assert.assertEquals(
        "https://smartregister.org/fhir/StructureMap?_id=1000,2000",
        requestDetails.getCompleteUrl());
    requestDetails.setRequestPath("StructureMap");

    Map<String, String[]> params = Maps.newHashMap();
    params.put("_id", new String[] {StringUtils.join(queryStringParamValues, ",")});
    requestDetails.setParameters(params);

    RequestMutation mutatedRequest =
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

    List<String> searchParamArrays =
        mutatedRequest.getQueryParams().get(ProxyConstants.TAG_SEARCH_PARAM);
    Assert.assertNotNull(searchParamArrays);
    for (int i = 0; i < mutatedRequest.getQueryParams().size(); i++) {
      Assert.assertTrue(
          organisationIds.contains(
              searchParamArrays.get(i).replace(ProxyConstants.ORGANISATION_TAG_URL + "|", "")));
    }
  }

  @Test(expected = RuntimeException.class)
  public void preprocessShouldThrowRuntimeExceptionWhenNoSyncStrategyFilterIsProvided() {
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetails requestDetails = new ServletRequestDetails();
    requestDetails.setRequestType(RequestTypeEnum.GET);
    requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
    requestDetails.setResourceName("Patient");
    requestDetails.setRequestPath("Patient");
    requestDetails.setFhirServerBase("https://smartregister.org/fhir");
    requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");

    // Call the method under testing
    testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));
  }

  @Test
  public void testPostProcessWithListModeHeaderShouldFetchListEntriesBundle() throws IOException {
    locationIds.add("Location-1");
    testInstance = Mockito.spy(createOpenSRPSyncAccessDecisionTestInstance());

    FhirContext fhirR4Context = mock(FhirContext.class);
    IGenericClient iGenericClient = mock(IGenericClient.class);
    ITransaction iTransaction = mock(ITransaction.class);
    ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);
    testInstance.setFhirR4Client(iGenericClient);
    testInstance.setFhirR4Context(fhirR4Context);

    Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
    Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

    Bundle resultBundle = new Bundle();
    resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
    resultBundle.setId("bundle-result-id");

    Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

    ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

    testInstance.setFhirR4Context(fhirR4Context);

    RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

    Mockito.when(requestDetailsSpy.getHeader(OpenSRPSyncAccessDecision.Constants.FHIR_GATEWAY_MODE))
        .thenReturn(OpenSRPSyncAccessDecision.Constants.LIST_ENTRIES);

    URL listUrl = Resources.getResource("test_list_resource.json");
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);

    String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

    Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
    Bundle requestBundle = bundleArgumentCaptor.getValue();

    // Verify modified request to the server
    Assert.assertNotNull(requestBundle);
    Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
    List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();
    Assert.assertEquals(2, requestBundleEntries.size());

    Assert.assertEquals(Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
    Assert.assertEquals(
        "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

    Assert.assertEquals(Bundle.HTTPVerb.GET, requestBundleEntries.get(1).getRequest().getMethod());
    Assert.assertEquals(
        "Group/proxy-list-entry-id-2", requestBundleEntries.get(1).getRequest().getUrl());

    // Verify returned result content from the server request
    Assert.assertNotNull(resultContent);
    Assert.assertEquals(
        "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\"}",
        resultContent);
  }

  @Test
  public void testPostProcessWithoutListModeHeaderShouldShouldReturnNull() throws IOException {
    testInstance = createOpenSRPSyncAccessDecisionTestInstance();

    RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);
    Mockito.when(requestDetailsSpy.getHeader(OpenSRPSyncAccessDecision.Constants.FHIR_GATEWAY_MODE))
        .thenReturn("");

    String resultContent =
        testInstance.postProcess(requestDetailsSpy, Mockito.mock(HttpResponse.class));

    // Verify no special Post-Processing happened
    Assert.assertNull(resultContent);
  }

  @Test
  public void testPostProcessWithListModeHeaderSearchByTagShouldFetchListEntriesBundle()
      throws IOException {
    locationIds.add("Location-1");
    testInstance = Mockito.spy(createOpenSRPSyncAccessDecisionTestInstance());

    FhirContext fhirR4Context = mock(FhirContext.class);
    IGenericClient iGenericClient = mock(IGenericClient.class);
    ITransaction iTransaction = mock(ITransaction.class);
    ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);

    Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
    Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

    Bundle resultBundle = new Bundle();
    resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
    resultBundle.setId("bundle-result-id");

    Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

    ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

    testInstance.setFhirR4Context(fhirR4Context);

    RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

    Mockito.when(requestDetailsSpy.getHeader(OpenSRPSyncAccessDecision.Constants.FHIR_GATEWAY_MODE))
        .thenReturn(OpenSRPSyncAccessDecision.Constants.LIST_ENTRIES);

    URL listUrl = Resources.getResource("test_list_resource.json");
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

    FhirContext realFhirContext = FhirContext.forR4();
    ListResource listResource =
        (ListResource) realFhirContext.newJsonParser().parseResource(testListJson);

    Bundle bundle = new Bundle();
    Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
    bundleEntryComponent.setResource(listResource);
    bundle.setType(Bundle.BundleType.BATCHRESPONSE);
    bundle.setEntry(Arrays.asList(bundleEntryComponent));

    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

    TestUtil.setUpFhirResponseMock(
        fhirResponseMock, realFhirContext.newJsonParser().encodeResourceToString(bundle));

    testInstance.setFhirR4Client(iGenericClient);
    testInstance.setFhirR4Context(fhirR4Context);
    String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

    Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
    Bundle requestBundle = bundleArgumentCaptor.getValue();

    // Verify modified request to the server
    Assert.assertNotNull(requestBundle);
    Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
    List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();
    Assert.assertEquals(2, requestBundleEntries.size());

    Assert.assertEquals(Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
    Assert.assertEquals(
        "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

    Assert.assertEquals(Bundle.HTTPVerb.GET, requestBundleEntries.get(1).getRequest().getMethod());
    Assert.assertEquals(
        "Group/proxy-list-entry-id-2", requestBundleEntries.get(1).getRequest().getUrl());

    // Verify returned result content from the server request
    Assert.assertNotNull(resultContent);
    Assert.assertEquals(
        "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\"}",
        resultContent);
  }

  @After
  public void cleanUp() {
    locationIds.clear();
    careTeamIds.clear();
    organisationIds.clear();
  }

  private OpenSRPSyncAccessDecision createOpenSRPSyncAccessDecisionTestInstance() {
    OpenSRPSyncAccessDecision accessDecision =
        new OpenSRPSyncAccessDecision(
            "sample-keycloak-id",
            "sample-application-id",
            true,
            locationIds,
            careTeamIds,
            organisationIds,
            null,
            userRoles);

    URL configFileUrl = Resources.getResource("hapi_sync_filter_ignored_queries.json");
    OpenSRPSyncAccessDecision.IgnoredResourcesConfig skippedDataFilterConfig =
        accessDecision.getIgnoredResourcesConfigFileConfiguration(configFileUrl.getPath());
    accessDecision.setSkippedResourcesConfig(skippedDataFilterConfig);
    return accessDecision;
  }
}
