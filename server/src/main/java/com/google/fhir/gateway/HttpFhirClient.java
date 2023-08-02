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
package com.google.fhir.gateway;

import static org.smartregister.utils.Constants.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;
import org.springframework.util.StreamUtils;

public abstract class HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpFhirClient.class);

  // The list of header names to keep in a response sent from the proxy; use lower case only.
  // Reference documentation - https://www.hl7.org/fhir/http.html#ops,
  // https://www.hl7.org/fhir/async.html
  // Note we don't copy content-length/type because we may modify the response.
  static final Set<String> RESPONSE_HEADERS_TO_KEEP =
      Sets.newHashSet(
          "last-modified",
          "date",
          "expires",
          "content-location",
          "content-encoding",
          "etag",
          "location",
          "x-progress",
          "x-request-id",
          "x-correlation-id");

  // The list of incoming header names to keep for forwarding request to the FHIR server; use lower
  // case only.
  // Reference documentation - https://www.hl7.org/fhir/http.html#ops,
  // https://www.hl7.org/fhir/async.html
  // We should NOT copy Content-Length as this is automatically set by the RequestBuilder when
  // setting content Entity; otherwise we will get a ClientProtocolException.
  // TODO(https://github.com/google/fhir-gateway/issues/60): Allow Accept header
  static final Set<String> REQUEST_HEADERS_TO_KEEP =
      Sets.newHashSet(
          "content-type",
          "accept-encoding",
          "last-modified",
          "etag",
          "prefer",
          "fhirVersion",
          "if-none-exist",
          "if-match",
          "if-none-match",
          // Condition header spec - https://www.rfc-editor.org/rfc/rfc7232#section-7.2
          "if-modified-since",
          "if-unmodified-since",
          "if-range",
          "x-request-id",
          "x-correlation-id",
          "x-forwarded-for",
          "x-forwarded-host");

  protected abstract String getBaseUrl();

  protected abstract URI getUriForResource(String resourcePath) throws URISyntaxException;

  protected abstract Header getAuthHeader();

  private FhirContext fhirR4Context = FhirContext.forR4();
  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

  private void setUri(RequestBuilder builder, String resourcePath) {
    try {
      URI uri = getUriForResource(resourcePath);
      builder.setUri(uri);
      logger.info("FHIR store resource is " + uri);
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error in building URI for resource " + resourcePath);
    }
  }

  HttpResponse handleRequest(ServletRequestDetails request) throws IOException {
    String httpMethod = request.getServletRequest().getMethod();
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    HttpResponse httpResponse;
    if (request.getRequestPath().contains("PractitionerDetails")) {
      setUri(
          builder,
          "Practitioner?identifier=" + request.getParameters().get("keycloak-uuid")[0].toString());

      byte[] requestContent = request.loadRequestContents();
      if (requestContent != null && requestContent.length > 0) {
        String contentType = request.getHeader("Content-Type");
        if (contentType == null) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger, "Content-Type header should be set for requests with body.");
        }
        builder.setEntity(new ByteArrayEntity(requestContent));
      }
      copyRequiredHeaders(request, builder);
      //      copyParameters(request, builder);
      httpResponse = sendRequest(builder);
      HttpEntity entity = httpResponse.getEntity();

      String responseString =
          StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
      httpResponse.setEntity(
          new StringEntity(responseString)); // Need this to reinstate the entity content

      // Create a FHIR context
      FhirContext ctx = FhirContext.forR4();
      IParser parser = ctx.newJsonParser();
      Bundle practitionerBundle = parser.parseResource(Bundle.class, responseString);
      List<Bundle.BundleEntryComponent> practitionersEntries =
          practitionerBundle != null ? practitionerBundle.getEntry() : new ArrayList<>();
      Practitioner practitioner =
          practitionersEntries != null && practitionersEntries.size() > 0
              ? (Practitioner) practitionersEntries.get(0).getResource()
              : null;
      String practitionerId = EMPTY_STRING;
      if (practitioner.getIdElement() != null && practitioner.getIdElement().getIdPart() != null) {
        practitionerId = practitioner.getIdElement().getIdPart();
      }
      List<Bundle.BundleEntryComponent> careTeamBundleEntryComponentList;
      List<CareTeam> careTeams = new ArrayList<>();

      List<Bundle.BundleEntryComponent> managingOrganizationBundleEntryComponentList;
      List<Organization> managingOrganizations = new ArrayList<>();

      if (StringUtils.isNotBlank(practitionerId)) {
        logger.info("Searching for care teams for practitioner with id: " + practitionerId);
        careTeamBundleEntryComponentList = getCareTeams(practitionerId);
        careTeams = mapToCareTeams(careTeamBundleEntryComponentList);
      }

      if (careTeams.size() == 0) {

        logger.info("Searching for Organizations tied with CareTeams: ");
        managingOrganizationBundleEntryComponentList =
            getManagingOrganizationsOfCareTeams(careTeams);
        managingOrganizations = mapToOrganization(managingOrganizationBundleEntryComponentList);
      }

      logger.info("Searching for organizations of practitioner with id: " + practitionerId);
      List<Bundle.BundleEntryComponent> organizationBundleEntryComponentList =
          getOrganizationsOfPractitioner(practitionerId);
      logger.info("Organizations are fetched");
      List<Organization> teams = mapToOrganization(organizationBundleEntryComponentList);

      List<Organization> bothOrganizations;
      // Add items from Lists into Set
      Set<Organization> set = new LinkedHashSet<>(managingOrganizations);
      set.addAll(teams);
      bothOrganizations = new ArrayList<>(set);

      List<Bundle.BundleEntryComponent> practitionerRolesBundleEntryList =
          getPractitionerRolesOfPractitioner(practitionerId);
      logger.info("Practitioner Roles are fetched");
      List<PractitionerRole> practitionerRoles =
          mapToPractitionerRoles(practitionerRolesBundleEntryList);

      List<Bundle.BundleEntryComponent> groupsBundleEntryList =
          getGroupsAssignedToAPractitioner(practitionerId);
      logger.info("Groups are fetched");
      List<Group> groups = mapToGroups(groupsBundleEntryList);

      logger.info("Searching for locations by organizations");
      List<String> locationsIdReferences = getLocationIdentifiersByOrganizations(bothOrganizations);
      List<String> locationIds = getLocationIdsFromReferences(locationsIdReferences);
      List<String> locationsIdentifiers = getLocationIdentifiersByIds(locationIds);
      logger.info("Searching for location hierarchy list by locations identifiers");
      //      List<LocationHierarchy> locationHierarchyList =
      //              getLocationsHierarchy(locationsIdentifiers);
      //      fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);
      logger.info("Searching for locations by ids");
      List<Location> locationsList = getLocationsByIds(locationIds);

      PractitionerDetails practitionerDetails = new PractitionerDetails();
      FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
      practitionerDetails.setId(practitionerId);
      fhirPractitionerDetails.setId(practitionerId);
      fhirPractitionerDetails.setCareTeams(careTeams);
      fhirPractitionerDetails.setPractitioners(Arrays.asList(practitioner));
      fhirPractitionerDetails.setGroups(groups);
      fhirPractitionerDetails.setLocations(locationsList);
      fhirPractitionerDetails.setLocationHierarchyList(Arrays.asList(new LocationHierarchy()));
      fhirPractitionerDetails.setPractitionerRoles(practitionerRoles);
      fhirPractitionerDetails.setOrganizationAffiliations(
          Arrays.asList(new OrganizationAffiliation()));
      fhirPractitionerDetails.setOrganizations(bothOrganizations);

      practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
      String resultContent = fhirR4JsonParser.encodeResourceToString(practitionerDetails);
      httpResponse.setEntity(new StringEntity(resultContent));
      return httpResponse;

    } else if (request.getRequestPath().contains("LocationHierarchy")) {
      setUri(
          builder,
          "Location?identifier=" + request.getParameters().get("identifier")[0].toString());
      byte[] requestContent = request.loadRequestContents();
      if (requestContent != null && requestContent.length > 0) {
        String contentType = request.getHeader("Content-Type");
        if (contentType == null) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger, "Content-Type header should be set for requests with body.");
        }
        builder.setEntity(new ByteArrayEntity(requestContent));
      }
      copyRequiredHeaders(request, builder);
      //      copyParameters(request, builder);
      httpResponse = sendRequest(builder);
      HttpEntity entity = httpResponse.getEntity();

      String responseString =
          StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
      httpResponse.setEntity(
          new StringEntity(responseString)); // Need this to reinstate the entity content

      JSONObject jsonObject = new JSONObject(responseString);
      System.out.println(responseString);

      return httpResponse;
    } else {
      setUri(builder, request.getRequestPath());

      // TODO Check why this does not work Content-Type is application/x-www-form-urlencoded.
      byte[] requestContent = request.loadRequestContents();
      if (requestContent != null && requestContent.length > 0) {
        String contentType = request.getHeader("Content-Type");
        if (contentType == null) {
          ExceptionUtil.throwRuntimeExceptionAndLog(
              logger, "Content-Type header should be set for requests with body.");
        }
        builder.setEntity(new ByteArrayEntity(requestContent));
      }
      copyRequiredHeaders(request, builder);
      copyParameters(request, builder);
      return sendRequest(builder);
    }
  }

  public HttpResponse getResource(String resourcePath) throws IOException {
    RequestBuilder requestBuilder = RequestBuilder.get();
    setUri(requestBuilder, resourcePath);
    return sendRequest(requestBuilder);
  }

  public HttpResponse patchResource(String resourcePath, String jsonPatch) throws IOException {
    Preconditions.checkArgument(jsonPatch != null && !jsonPatch.isEmpty());
    RequestBuilder requestBuilder = RequestBuilder.patch();
    setUri(requestBuilder, resourcePath);
    byte[] content = jsonPatch.getBytes(Constants.CHARSET_UTF8);
    requestBuilder.setCharset(Constants.CHARSET_UTF8);
    requestBuilder.setEntity(new ByteArrayEntity(content, ProxyConstants.JSON_PATCH_CONTENT));
    return sendRequest(requestBuilder);
  }

  private HttpResponse sendRequest(RequestBuilder builder) throws IOException {
    Preconditions.checkArgument(builder.getFirstHeader("Authorization") == null);
    Header header = getAuthHeader();
    builder.addHeader(header);
    HttpUriRequest httpRequest = builder.build();
    logger.info("Request to the FHIR store is {}", httpRequest);
    // TODO reuse if creation overhead is significant.
    HttpClient httpClient = HttpClients.createDefault();

    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(httpRequest);
    if (response.getStatusLine().getStatusCode() >= 400) {
      logger.error(
          String.format(
              "Error in FHIR resource %s method %s; status %s",
              httpRequest.getRequestLine(),
              httpRequest.getMethod(),
              response.getStatusLine().toString()));
    }
    return response;
  }

  List<Header> responseHeadersToKeep(HttpResponse response) {
    List<Header> headers = Lists.newArrayList();
    for (Header header : response.getAllHeaders()) {
      if (RESPONSE_HEADERS_TO_KEEP.contains(header.getName().toLowerCase())) {
        headers.add(header);
      }
    }
    return headers;
  }

  @VisibleForTesting
  void copyRequiredHeaders(ServletRequestDetails request, RequestBuilder builder) {
    for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
      if (REQUEST_HEADERS_TO_KEEP.contains(entry.getKey().toLowerCase())) {
        for (String value : entry.getValue()) {
          builder.addHeader(entry.getKey(), value);
        }
      }
    }
  }

  @VisibleForTesting
  void copyParameters(ServletRequestDetails request, RequestBuilder builder) {
    // TODO Check if we can directly do this by copying
    // request.getServletRequest().getQueryString().
    for (Map.Entry<String, String[]> entry : request.getParameters().entrySet()) {
      for (String val : entry.getValue()) {
        builder.addParameter(entry.getKey(), val);
      }
    }
  }

  private List<Bundle.BundleEntryComponent> getCareTeams(String practitionerId) throws IOException {
    String httpMethod = "GET";
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    HttpResponse httpResponse;
    setUri(builder, "CareTeam?participant=" + practitionerId);
    httpResponse = sendRequest(builder);
    HttpEntity entity = httpResponse.getEntity();

    String responseString = StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
    httpResponse.setEntity(
        new StringEntity(responseString)); // Need this to reinstate the entity content
    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser();
    Bundle careTeamBundle = parser.parseResource(Bundle.class, responseString);
    List<Bundle.BundleEntryComponent> careTeamEntries =
        careTeamBundle != null ? careTeamBundle.getEntry() : new ArrayList<>();
    return careTeamEntries;
  }

  private List<CareTeam> mapToCareTeams(List<Bundle.BundleEntryComponent> careTeamEntries) {
    List<CareTeam> careTeamList = new ArrayList<>();
    CareTeam careTeamObject;
    for (Bundle.BundleEntryComponent careTeamEntryComponent : careTeamEntries) {
      careTeamObject = (CareTeam) careTeamEntryComponent.getResource();
      careTeamList.add(careTeamObject);
    }
    return careTeamList;
  }

  private List<Practitioner> mapToPractitioners(
      List<Bundle.BundleEntryComponent> practitionerEntries) {
    List<Practitioner> practitionerList = new ArrayList<>();
    Practitioner practitioner;
    for (Bundle.BundleEntryComponent practitionerEntry : practitionerEntries) {
      practitioner = (Practitioner) practitionerEntry.getResource();
      practitionerList.add(practitioner);
    }
    return practitionerList;
  }

  private List<Bundle.BundleEntryComponent> getManagingOrganizationsOfCareTeams(
      List<CareTeam> careTeamsList) throws IOException {
    List<String> organizationIdReferences = new ArrayList<>();
    List<Reference> managingOrganizations = new ArrayList<>();
    for (CareTeam careTeam : careTeamsList) {
      if (careTeam.hasManagingOrganization()) {
        managingOrganizations.addAll(careTeam.getManagingOrganization());
      }
    }
    for (Reference managingOrganization : managingOrganizations) {
      if (managingOrganization != null && managingOrganization.getReference() != null) {
        organizationIdReferences.add(managingOrganization.getReference());
      }
    }
    return searchOrganizationsById(organizationIdReferences);
  }

  private List<Bundle.BundleEntryComponent> searchOrganizationsById(
      List<String> organizationIdsReferences) throws IOException {
    List<String> organizationIds = getOrganizationIdsFromReferences(organizationIdsReferences);
    logger.info("Making a list of identifiers from organization identifiers");
    Bundle organizationsBundle;
    List<String> theIdList = new ArrayList<String>();
    if (organizationIds.size() > 0) {
      for (String organizationId : organizationIds) {
        theIdList.add(organizationId);
        logger.info("Added organization id : " + organizationId + " in a list");
      }
      logger.info(
          "Now hitting organization search end point with the idslist param of size: "
              + theIdList.size());
      String httpMethod = "GET";
      RequestBuilder builder = RequestBuilder.create(httpMethod);
      HttpResponse httpResponse;
      String ids = getCommaSeparatedList(theIdList);
      setUri(builder, "Organization?_id=" + ids);
      httpResponse = sendRequest(builder);
      HttpEntity entity = httpResponse.getEntity();

      String responseString =
          StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
      httpResponse.setEntity(
          new StringEntity(responseString)); // Need this to reinstate the entity content
      FhirContext ctx = FhirContext.forR4();
      IParser parser = ctx.newJsonParser();
      organizationsBundle = parser.parseResource(Bundle.class, responseString);
      List<Bundle.BundleEntryComponent> organizationEntries =
          organizationsBundle != null ? organizationsBundle.getEntry() : new ArrayList<>();
      return organizationEntries;
    } else {
      return new ArrayList<>();
    }
  }

  private List<Organization> mapToOrganization(
      List<Bundle.BundleEntryComponent> organizationEntries) {
    List<Organization> organizationList = new ArrayList<>();
    Organization organization;
    for (Bundle.BundleEntryComponent organizationObj : organizationEntries) {
      organization = (Organization) organizationObj.getResource();
      organizationList.add(organization);
    }
    return organizationList;
  }

  private List<String> getOrganizationIdsFromReferences(List<String> organizationReferences) {
    return getResourceIds(organizationReferences);
  }

  @NotNull
  private List<String> getResourceIds(List<String> resourceReferences) {
    List<String> resourceIds = new ArrayList<>();
    for (String reference : resourceReferences) {
      if (reference.contains(FORWARD_SLASH)) {
        reference = reference.substring(reference.indexOf(FORWARD_SLASH) + 1);
      }
      resourceIds.add(reference);
    }
    return resourceIds;
  }

  private List<Bundle.BundleEntryComponent> getOrganizationsOfPractitioner(String practitionerId)
      throws IOException {
    List<String> organizationIdsReferences = getOrganizationIds(practitionerId);
    logger.info(
        "Organization Ids are retrieved, found to be of size: " + organizationIdsReferences.size());

    return searchOrganizationsById(organizationIdsReferences);
  }

  private List<String> getOrganizationIds(String practitionerId) throws IOException {
    List<Bundle.BundleEntryComponent> practitionerRolesBundleEntries =
        getPractitionerRolesOfPractitioner(practitionerId);
    List<String> organizationIdsString = new ArrayList<>();
    if (practitionerRolesBundleEntries.size() > 0) {
      for (Bundle.BundleEntryComponent practitionerRoleEntryComponent :
          practitionerRolesBundleEntries) {
        PractitionerRole pRole = (PractitionerRole) practitionerRoleEntryComponent.getResource();
        if (pRole != null
            && pRole.getOrganization() != null
            && pRole.getOrganization().getReference() != null) {
          organizationIdsString.add(pRole.getOrganization().getReference());
        }
      }
    }
    return organizationIdsString;
  }

  private List<Bundle.BundleEntryComponent> getPractitionerRolesOfPractitioner(
      String practitionerId) throws IOException {
    String httpMethod = "GET";
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    HttpResponse httpResponse;
    setUri(builder, "PractitionerRole?practitioner=" + practitionerId);
    httpResponse = sendRequest(builder);
    HttpEntity entity = httpResponse.getEntity();

    String responseString = StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
    httpResponse.setEntity(
        new StringEntity(responseString)); // Need this to reinstate the entity content
    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser();
    Bundle practitionerRoleBundle = parser.parseResource(Bundle.class, responseString);
    List<Bundle.BundleEntryComponent> practitionerRoleEntries =
        practitionerRoleBundle != null ? practitionerRoleBundle.getEntry() : new ArrayList<>();
    return practitionerRoleEntries;
  }

  private List<PractitionerRole> mapToPractitionerRoles(
      List<Bundle.BundleEntryComponent> practitionerRoles) {

    List<PractitionerRole> practitionerRoleList = new ArrayList<>();
    PractitionerRole practitionerRole;
    for (Bundle.BundleEntryComponent practitionerRoleObj : practitionerRoles) {
      practitionerRole = (PractitionerRole) practitionerRoleObj.getResource();
      practitionerRoleList.add(practitionerRole);
    }
    return practitionerRoleList;
  }

  private List<Bundle.BundleEntryComponent> getGroupsAssignedToAPractitioner(String practitionerId)
      throws IOException {
    String httpMethod = "GET";
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    HttpResponse httpResponse;
    setUri(builder, "Group?code=405623001&member=" + practitionerId);
    httpResponse = sendRequest(builder);
    HttpEntity entity = httpResponse.getEntity();

    String responseString = StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
    httpResponse.setEntity(
        new StringEntity(responseString)); // Need this to reinstate the entity content
    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser();
    Bundle groupsBundle = parser.parseResource(Bundle.class, responseString);
    List<Bundle.BundleEntryComponent> groupsEntries =
        groupsBundle != null ? groupsBundle.getEntry() : new ArrayList<>();
    return groupsEntries;
  }

  private List<Group> mapToGroups(List<Bundle.BundleEntryComponent> groupsEntries) {
    List<Group> groupList = new ArrayList<>();
    Group groupObj;
    for (Bundle.BundleEntryComponent groupEntry : groupsEntries) {
      groupObj = (Group) groupEntry.getResource();
      groupList.add(groupObj);
    }
    return groupList;
  }

  private List<String> getLocationIdentifiersByOrganizations(List<Organization> organizations)
      throws IOException {
    List<String> locationsIdentifiers = new ArrayList<>();
    Set<String> locationsIdentifiersSet = new HashSet<>();
    logger.info("Traversing organizations");
    for (Organization team : organizations) {
      String httpMethod = "GET";
      RequestBuilder builder = RequestBuilder.create(httpMethod);
      HttpResponse httpResponse;

      logger.info("Searching organization affiliation from organization id: " + team.getId());

      setUri(builder, "OrganizationAffiliation?primary-organization=" + team.getId());
      httpResponse = sendRequest(builder);
      HttpEntity entity = httpResponse.getEntity();

      String responseString =
          StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
      httpResponse.setEntity(
          new StringEntity(responseString)); // Need this to reinstate the entity content
      FhirContext ctx = FhirContext.forR4();
      IParser parser = ctx.newJsonParser();
      Bundle organizationAffiliationBundle = parser.parseResource(Bundle.class, responseString);
      List<Bundle.BundleEntryComponent> organizationAffiliationEntries =
          organizationAffiliationBundle != null
              ? organizationAffiliationBundle.getEntry()
              : new ArrayList<>();
      List<OrganizationAffiliation> organizationAffiliations =
          mapToOrganizationAffiliation(organizationAffiliationEntries);
      OrganizationAffiliation organizationAffiliationObj;
      if (organizationAffiliations.size() > 0) {
        for (IBaseResource organizationAffiliation : organizationAffiliations) {
          organizationAffiliationObj = (OrganizationAffiliation) organizationAffiliation;
          List<Reference> locationList = organizationAffiliationObj.getLocation();
          for (Reference location : locationList) {
            if (location != null
                && location.getReference() != null
                && locationsIdentifiersSet != null) {
              locationsIdentifiersSet.add(location.getReference());
            }
          }
        }
      }
    }
    locationsIdentifiers = new ArrayList<>(locationsIdentifiersSet);
    return locationsIdentifiers;
  }

  private List<OrganizationAffiliation> mapToOrganizationAffiliation(
      List<Bundle.BundleEntryComponent> organnizationAffiliationEntries) {
    List<OrganizationAffiliation> organizationAffiliationList = new ArrayList<>();
    OrganizationAffiliation organizationAffiliation;
    for (Bundle.BundleEntryComponent organizationAffiliationEntry :
        organnizationAffiliationEntries) {
      organizationAffiliation =
          (OrganizationAffiliation) organizationAffiliationEntry.getResource();
      organizationAffiliationList.add(organizationAffiliation);
    }
    return organizationAffiliationList;
  }

  private List<String> getLocationIdsFromReferences(List<String> locationReferences) {
    return getResourceIds(locationReferences);
  }

  private List<String> getLocationIdentifiersByIds(List<String> locationIds) throws IOException {
    List<String> locationsIdentifiers = new ArrayList<>();
    for (String locationId : locationIds) {
      List<Location> locationsResources = generateLocationResource(locationId);
      for (Location locationResource : locationsResources) {
        locationsIdentifiers.addAll(
            locationResource.getIdentifier().stream()
                .map(this::getLocationIdentifierValue)
                .collect(Collectors.toList()));
      }
    }
    return locationsIdentifiers;
  }

  private List<Location> generateLocationResource(String locationId) throws IOException {
    String httpMethod = "GET";
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    HttpResponse httpResponse;
    setUri(builder, "Location?_id" + locationId);
    httpResponse = sendRequest(builder);
    HttpEntity entity = httpResponse.getEntity();

    String responseString = StreamUtils.copyToString(entity.getContent(), Charset.forName("UTF-8"));
    httpResponse.setEntity(
        new StringEntity(responseString)); // Need this to reinstate the entity content
    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser();
    Bundle locationBundle = parser.parseResource(Bundle.class, responseString);
    List<Bundle.BundleEntryComponent> locationEntries =
        locationBundle != null ? locationBundle.getEntry() : new ArrayList<>();
    return mapToLocation(locationEntries);
  }

  private List<Location> mapToLocation(List<Bundle.BundleEntryComponent> locationEntries) {
    List<Location> locationsList = new ArrayList<>();
    Location organizationAffiliation;
    for (Bundle.BundleEntryComponent locationEntry : locationEntries) {
      organizationAffiliation = (Location) locationEntry.getResource();
      locationsList.add(organizationAffiliation);
    }
    return locationsList;
  }

  private String getLocationIdentifierValue(Identifier locationIdentifier) {
    if (locationIdentifier.getUse() != null
        && locationIdentifier.getUse().equals(Identifier.IdentifierUse.OFFICIAL)) {
      return locationIdentifier.getValue();
    }
    return EMPTY_STRING;
  }

  private List<Location> getLocationsByIds(List<String> locationIds) throws IOException {
    List<Location> locations = new ArrayList<>();
    for (String locationId : locationIds) {
      Location location;
      for (IBaseResource locationResource : generateLocationResource(locationId)) {
        location = (Location) locationResource;
        locations.add(location);
      }
    }
    return locations;
  }

  public static String getCommaSeparatedList(List<String> numbers) {
    StringBuilder commaSeparatedList = new StringBuilder();
    for (String number : numbers) {
      commaSeparatedList.append(number).append(",");
    }
    commaSeparatedList.delete(commaSeparatedList.length() - 1, commaSeparatedList.length());
    return commaSeparatedList.toString();
  }
}
