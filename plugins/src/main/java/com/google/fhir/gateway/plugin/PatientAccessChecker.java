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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.fhir.gateway.BundlePatients;
import com.google.fhir.gateway.FhirUtil;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_id` and the `scope` claim in the access token to decide
 * whether access to a request should be granted or not. The `scope` claims are expected to tbe
 * SMART-on-FHIR compliant.
 */
public class PatientAccessChecker implements AccessChecker {
  private static final Logger logger = LoggerFactory.getLogger(PatientAccessChecker.class);

  private final String authorizedPatientId;
  private final PatientFinder patientFinder;

  private final FhirContext fhirContext;
  private final Map<String, Set<SmartFhirScopeResourcePermission>> permissionsByResourceType;

  private PatientAccessChecker(
      FhirContext fhirContext,
      String authorizedPatientId,
      PatientFinder patientFinder,
      List<SmartFhirScope> scopes) {
    Preconditions.checkNotNull(authorizedPatientId);
    Preconditions.checkNotNull(patientFinder);
    Preconditions.checkNotNull(scopes);
    Preconditions.checkNotNull(fhirContext);
    this.authorizedPatientId = authorizedPatientId;
    this.patientFinder = patientFinder;
    this.fhirContext = fhirContext;
    this.permissionsByResourceType =
        scopes.stream()
            .collect(
                Collectors.groupingBy(
                    SmartFhirScope::getResourceType,
                    Collectors.flatMapping(
                        resourceScopes -> resourceScopes.getResourcePermissions().stream(),
                        Collectors.toSet())));
  }

  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
    // For a Bundle requestDetails.getResourceName() returns null
    if (requestDetails.getRequestType() == RequestTypeEnum.POST
        && requestDetails.getResourceName() == null) {
      return processBundle(requestDetails);
    }

    switch (requestDetails.getRequestType()) {
      case GET:
        return processGet(requestDetails);
      case POST:
        return processPost(requestDetails);
      case PUT:
        // TODO(https://github.com/google/fhir-gateway/issues/88): Support update as create
        // operation
      case PATCH:
        return processUpdate(requestDetails);
      case DELETE:
        return processDelete(requestDetails);
      default:
        return NoOpAccessDecision.accessDenied();
    }
  }

  private AccessDecision processGet(RequestDetailsReader requestDetails) {
    if (requestDetails.getResourceName() == null) {
      return NoOpAccessDecision.accessDenied();
    }
    // This operation corresponds to the read/vread/history operations on instance
    if (requestDetails.getId() != null) {
      return processRead(requestDetails);
    }
    return processSearch(requestDetails);
  }

  private AccessDecision processPost(RequestDetailsReader requestDetails) {
    // TODO(https://github.com/google/fhir-gateway/issues/87): Add support for search in post
    return processCreate(requestDetails);
  }

  private boolean hasPermission(String resourceType, SmartFhirScopeResourcePermission permission) {
    return this.permissionsByResourceType
            .getOrDefault(resourceType, Collections.emptySet())
            .contains(permission)
        || this.permissionsByResourceType
            .getOrDefault(SmartFhirScope.ALL_RESOURCE_TYPES_WILDCARD, Collections.emptySet())
            .contains(permission);
  }

  private AccessDecision processRead(RequestDetailsReader requestDetails) {
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && hasPermission(
                requestDetails.getResourceName(), SmartFhirScopeResourcePermission.READ));
  }

  private AccessDecision processSearch(RequestDetailsReader requestDetails) {
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && hasPermission(
                requestDetails.getResourceName(), SmartFhirScopeResourcePermission.SEARCH));
  }

  private AccessDecision processCreate(RequestDetailsReader requestDetails) {
    // This AccessChecker does not accept new patients.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(
        patientIds.contains(authorizedPatientId)
            && hasPermission(
                requestDetails.getResourceName(), SmartFhirScopeResourcePermission.CREATE));
  }

  private AccessDecision processUpdate(RequestDetailsReader requestDetails) {
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return checkPatientAccessInUpdate(requestDetails);
    }
    return checkNonPatientAccessInUpdate(requestDetails, requestDetails.getRequestType());
  }

  private AccessDecision processDelete(RequestDetailsReader requestDetails) {
    // This AccessChecker does not allow deletion of Patient resource
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    // TODO(https://github.com/google/fhir-access-proxy/issues/63):Support direct resource deletion.
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && hasPermission(
                requestDetails.getResourceName(), SmartFhirScopeResourcePermission.DELETE));
  }

  private AccessDecision checkNonPatientAccessInUpdate(
      RequestDetailsReader requestDetails, RequestTypeEnum updateMethod) {
    // We do not allow direct resource PUT/PATCH, so Patient ID must be returned
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    if (!patientId.equals(authorizedPatientId)) {
      return NoOpAccessDecision.accessDenied();
    }

    Set<String> patientIds = Sets.newHashSet();
    if (updateMethod == RequestTypeEnum.PATCH) {
      patientIds =
          patientFinder.findPatientsInPatch(requestDetails, requestDetails.getResourceName());
      if (patientIds.isEmpty()) {
        return NoOpAccessDecision.accessGranted();
      }
    }
    if (updateMethod == RequestTypeEnum.PUT) {
      patientIds = patientFinder.findPatientsInResource(requestDetails);
    }
    return new NoOpAccessDecision(
        patientIds.contains(authorizedPatientId)
            && hasPermission(
                requestDetails.getResourceName(), SmartFhirScopeResourcePermission.UPDATE));
  }

  private AccessDecision checkPatientAccessInUpdate(RequestDetailsReader requestDetails) {
    String patientId = FhirUtil.getIdOrNull(requestDetails);
    if (patientId == null) {
      // This is an invalid PUT request; note we are not supporting "conditional updates".
      logger.error("The provided Patient resource has no ID; denying access!");
      return NoOpAccessDecision.accessDenied();
    }
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && hasPermission(ResourceType.Patient.name(), SmartFhirScopeResourcePermission.UPDATE));
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) {
    BundlePatients patientsInBundle = patientFinder.findPatientsInBundle(requestDetails);

    if (patientsInBundle == null || patientsInBundle.areTherePatientToCreate()) {
      return NoOpAccessDecision.accessDenied();
    }

    if (!patientsInBundle.getUpdatedPatients().isEmpty()
        && !patientsInBundle.getUpdatedPatients().equals(ImmutableSet.of(authorizedPatientId))) {
      return NoOpAccessDecision.accessDenied();
    }

    for (Set<String> refSet : patientsInBundle.getReferencedPatients()) {
      if (!refSet.contains(authorizedPatientId)) {
        return NoOpAccessDecision.accessDenied();
      }
    }

    Bundle requestBundle = createBundleFromRequest(requestDetails);
    if (requestBundle == null) {
      return NoOpAccessDecision.accessDenied();
    }
    for (BundleEntryComponent entryComponent : requestBundle.getEntry()) {
      if (!doesBundleElementHavePermission(entryComponent)) {
        return NoOpAccessDecision.accessDenied();
      }
    }
    return NoOpAccessDecision.accessGranted();
  }

  private Bundle createBundleFromRequest(RequestDetailsReader request) {
    byte[] requestContentBytes = request.loadRequestContents();
    Charset charset = request.getCharset();
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    String requestContent = new String(requestContentBytes, charset);
    IParser jsonParser = fhirContext.newJsonParser();
    IBaseResource resource = jsonParser.parseResource(requestContent);
    if (!(resource instanceof Bundle)) {
      return null;
    }
    return (Bundle) resource;
  }

  private boolean doesReferenceElementHavePermission(
      IIdType referenceElement, SmartFhirScopeResourcePermission permission) {
    if (referenceElement.getResourceType() != null && referenceElement.hasIdPart()) {
      return hasPermission(referenceElement.getResourceType(), permission);
    } else {
      return hasPermission(referenceElement.getValue(), permission);
    }
  }

  private boolean doesBundleElementHavePermission(BundleEntryComponent bundleEntry) {
    BundleEntryRequestComponent bundleEntryRequest = bundleEntry.getRequest();
    try {
      switch (bundleEntryRequest.getMethod()) {
        case GET:
          if (bundleEntryRequest.getUrl() != null) {
            URI resourceUri = new URI(bundleEntryRequest.getUrl());
            IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
            return doesReferenceElementHavePermission(
                referenceElement, SmartFhirScopeResourcePermission.READ);
          }
          break;
        case POST:
          if (bundleEntry.getResource().getResourceType() != null) {
            return hasPermission(
                bundleEntry.getResource().getResourceType().name(),
                SmartFhirScopeResourcePermission.CREATE);
          }
          // TODO(https://github.com/google/fhir-gateway/issues/87): Add support for search in post
          break;
        case PUT:
          if (bundleEntryRequest.getUrl() != null) {
            URI resourceUri = new URI(bundleEntryRequest.getUrl());
            IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
            return doesReferenceElementHavePermission(
                referenceElement, SmartFhirScopeResourcePermission.UPDATE);
          }
          break;
        case PATCH:
          if (bundleEntryRequest.getUrl() != null) {
            URI resourceUri = new URI(bundleEntryRequest.getUrl());
            IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
            return doesReferenceElementHavePermission(
                referenceElement, SmartFhirScopeResourcePermission.UPDATE);
          }
        default:
          return false;
      }
    } catch (URISyntaxException e) {
      logger.error(
          String.format("Error in parsing bundle request url %s", bundleEntryRequest.getUrl()));
    }
    return false;
  }

  @Named(value = "patient")
  static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String PATIENT_CLAIM = "patient_id";

    @VisibleForTesting static final String SCOPES_CLAIM = "scope";

    private String getPatientId(DecodedJWT jwt) {
      return FhirUtil.checkIdOrFail(JwtUtil.getClaimOrDie(jwt, PATIENT_CLAIM));
    }

    private List<SmartFhirScope> getPatientScopes(DecodedJWT jwt) {
      String scopesClaim = JwtUtil.getClaimOrDie(jwt, SCOPES_CLAIM);
      String[] scopes = scopesClaim.strip().split("\\s+");
      return SmartFhirScope.extractSmartFhirScopesFromTokens(Arrays.asList(scopes)).stream()
          .filter(
              smartFhirScope ->
                  smartFhirScope.principalContext == SmartFhirScopeResourcePrincipalContext.PATIENT)
          .collect(Collectors.toList());
    }

    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      return new PatientAccessChecker(
          fhirContext, getPatientId(jwt), patientFinder, getPatientScopes(jwt));
    }
  }
}
