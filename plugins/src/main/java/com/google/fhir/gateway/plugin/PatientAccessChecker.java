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
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.fhir.gateway.BundleEntryPatient;
import com.google.fhir.gateway.BundleProcessor;
import com.google.fhir.gateway.FhirUtil;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.BundleEntryPatientFinder;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.plugin.SmartFhirScope.Permission;
import com.google.fhir.gateway.plugin.SmartFhirScope.Principal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Named;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_id` and `scope` claims in the access token to decide
 * whether access to a request should be granted or not. The `scope` claims are expected to tbe
 * SMART-on-FHIR compliant.
 */
public class PatientAccessChecker implements AccessChecker {
  private static final Logger logger = LoggerFactory.getLogger(PatientAccessChecker.class);

  private final String authorizedPatientId;
  private final PatientFinder patientFinder;

  private final BundleEntryPatientFinder bundleEntryPatientFinder;

  private final FhirContext fhirContext;

  private final SmartScopeChecker smartScopeChecker;
  private final BundleProcessor bundleProcessor;

  private PatientAccessChecker(
      FhirContext fhirContext,
      String authorizedPatientId,
      PatientFinder patientFinder,
      SmartScopeChecker smartScopeChecker,
      BundleEntryPatientFinder bundleEntryPatientFinder) {
    Preconditions.checkNotNull(authorizedPatientId);
    Preconditions.checkNotNull(patientFinder);
    Preconditions.checkNotNull(smartScopeChecker);
    Preconditions.checkNotNull(fhirContext);
    this.authorizedPatientId = authorizedPatientId;
    this.patientFinder = patientFinder;
    this.fhirContext = fhirContext;
    this.smartScopeChecker = smartScopeChecker;
    this.bundleEntryPatientFinder = bundleEntryPatientFinder;
    this.bundleProcessor = new BundleProcessor(fhirContext);
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

  private AccessDecision processRead(RequestDetailsReader requestDetails) {
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && smartScopeChecker.hasPermission(requestDetails.getResourceName(), Permission.READ));
  }

  private AccessDecision processSearch(RequestDetailsReader requestDetails) {
    String patientId = patientFinder.findPatientFromParams(requestDetails);
    return new NoOpAccessDecision(
        authorizedPatientId.equals(patientId)
            && smartScopeChecker.hasPermission(
                requestDetails.getResourceName(), Permission.SEARCH));
  }

  private AccessDecision processCreate(RequestDetailsReader requestDetails) {
    // This AccessChecker does not accept new patients.
    if (FhirUtil.isSameResourceType(requestDetails.getResourceName(), ResourceType.Patient)) {
      return NoOpAccessDecision.accessDenied();
    }
    Set<String> patientIds = patientFinder.findPatientsInResource(requestDetails);
    return new NoOpAccessDecision(
        patientIds.contains(authorizedPatientId)
            && smartScopeChecker.hasPermission(
                requestDetails.getResourceName(), Permission.CREATE));
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
            && smartScopeChecker.hasPermission(
                requestDetails.getResourceName(), Permission.DELETE));
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
            && smartScopeChecker.hasPermission(
                requestDetails.getResourceName(), Permission.UPDATE));
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
            && smartScopeChecker.hasPermission(ResourceType.Patient.name(), Permission.UPDATE));
  }

  private AccessDecision processBundle(RequestDetailsReader requestDetails) {
    Map<HTTPVerb, Consumer<BundleEntryComponent>> bundleEntryProcessors = new HashMap<>();
    BundleAccessDecision bundleAccessDecision = new BundleAccessDecision();
    bundleEntryProcessors.put(
        HTTPVerb.GET,
        bundleEntry -> {
          BundleEntryPatient patientEntry =
              bundleEntryPatientFinder.processGetBundleEntry(bundleEntry);
          bundleAccessDecision.setBundleEntryDecision(
              doesGetBundleEntryHaveAccess(bundleEntry, patientEntry));
        });
    bundleEntryProcessors.put(
        HTTPVerb.DELETE,
        bundleEntry -> {
          BundleEntryPatient patientEntry =
              bundleEntryPatientFinder.processDeleteBundleEntry(bundleEntry);
          bundleAccessDecision.setBundleEntryDecision(
              doesDeleteBundleEntryHaveAccess(bundleEntry, patientEntry));
        });
    bundleEntryProcessors.put(
        HTTPVerb.POST,
        bundleEntry -> {
          BundleEntryPatient patientEntry =
              bundleEntryPatientFinder.processPostBundleEntry(bundleEntry);
          bundleAccessDecision.setBundleEntryDecision(
              doesPostBundleEntryHaveAccess(bundleEntry, patientEntry));
        });
    bundleEntryProcessors.put(
        HTTPVerb.PUT,
        bundleEntry -> {
          BundleEntryPatient patientEntry =
              bundleEntryPatientFinder.processPutBundleEntry(bundleEntry);
          bundleAccessDecision.setBundleEntryDecision(
              doesPutBundleEntryHaveAccess(bundleEntry, patientEntry));
        });
    bundleEntryProcessors.put(
        HTTPVerb.PATCH,
        bundleEntry -> {
          BundleEntryPatient patientEntry =
              bundleEntryPatientFinder.processPatchBundleEntry(bundleEntry);
          bundleAccessDecision.setBundleEntryDecision(
              doesPatchBundleEntryHaveAccess(bundleEntry, patientEntry));
        });
    boolean accessGranted =
        bundleProcessor.processBundleFromRequest(
            requestDetails,
            bundleEntryProcessors,
            bundleAccessDecision::areAllBundleOperationsAllowed);

    if (accessGranted) {
      return NoOpAccessDecision.accessGranted();
    }
    return NoOpAccessDecision.accessDenied();
  }

  private boolean doesGetBundleEntryHaveAccess(
      BundleEntryComponent bundleEntryComponent, BundleEntryPatient patientEntry) {
    return patientEntry.getReferencedPatients().contains(authorizedPatientId)
        && doesBundleReferenceElementHavePermission(bundleEntryComponent, Permission.READ);
  }

  private boolean doesDeleteBundleEntryHaveAccess(
      BundleEntryComponent bundleEntryComponent, BundleEntryPatient patientEntry) {
    if (patientEntry.getPatientModification() != null) {
      return false;
    }
    return patientEntry.getReferencedPatients().contains(authorizedPatientId)
        && doesBundleReferenceElementHavePermission(bundleEntryComponent, Permission.DELETE);
  }

  private boolean doesPostBundleEntryHaveAccess(
      BundleEntryComponent bundleEntryComponent, BundleEntryPatient patientEntry) {
    if (patientEntry.getPatientModification() != null) {
      return false;
    }
    if (bundleEntryComponent.getResource().getResourceType() != null) {
      return patientEntry.getReferencedPatients().contains(authorizedPatientId)
          && smartScopeChecker.hasPermission(
              bundleEntryComponent.getResource().getResourceType().name(), Permission.CREATE);
    }
    // TODO(https://github.com/google/fhir-gateway/issues/87): Add support for search in post
    return false;
  }

  private boolean doesPutBundleEntryHaveAccess(
      BundleEntryComponent bundleEntryComponent, BundleEntryPatient patientEntry) {
    if (patientEntry.getPatientModification() != null) {
      return patientEntry
              .getPatientModification()
              .getModifiedPatientIds()
              .equals(ImmutableSet.of(authorizedPatientId))
          && smartScopeChecker.hasPermission(ResourceType.Patient.name(), Permission.UPDATE);
    }
    return patientEntry.getReferencedPatients().contains(authorizedPatientId)
        && doesBundleReferenceElementHavePermission(bundleEntryComponent, Permission.UPDATE);
  }

  private boolean doesPatchBundleEntryHaveAccess(
      BundleEntryComponent bundleEntryComponent, BundleEntryPatient patientEntry) {
    if (patientEntry.getPatientModification() != null) {
      return patientEntry
              .getPatientModification()
              .getModifiedPatientIds()
              .equals(ImmutableSet.of(authorizedPatientId))
          && smartScopeChecker.hasPermission(ResourceType.Patient.name(), Permission.UPDATE);
    }
    return patientEntry.getReferencedPatients().contains(authorizedPatientId)
        && doesBundleReferenceElementHavePermission(bundleEntryComponent, Permission.UPDATE);
  }

  private boolean doesBundleReferenceElementHavePermission(
      BundleEntryComponent bundleEntryComponent, Permission permission) {
    BundleEntryRequestComponent bundleEntryRequest = bundleEntryComponent.getRequest();
    try {
      if (bundleEntryRequest.getUrl() != null) {
        URI resourceUri = new URI(bundleEntryRequest.getUrl());
        IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
        if (referenceElement.getResourceType() != null && referenceElement.hasIdPart()) {
          return smartScopeChecker.hasPermission(referenceElement.getResourceType(), permission);
        } else {
          return smartScopeChecker.hasPermission(referenceElement.getValue(), permission);
        }
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

    private SmartScopeChecker getSmartFhirPermissionChecker(DecodedJWT jwt) {
      String scopesClaim = JwtUtil.getClaimOrDie(jwt, SCOPES_CLAIM);
      String[] scopes = scopesClaim.strip().split("\\s+");
      return new SmartScopeChecker(
          SmartFhirScope.extractSmartFhirScopesFromTokens(Arrays.asList(scopes)),
          Principal.PATIENT);
    }

    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder,
        BundleEntryPatientFinder bundleEntryPatientFinder) {
      return new PatientAccessChecker(
          fhirContext,
          getPatientId(jwt),
          patientFinder,
          getSmartFhirPermissionChecker(jwt),
          bundleEntryPatientFinder);
    }
  }

  private class BundleAccessDecision {
    private boolean allBundleOperationsAllowed = true;

    void setBundleEntryDecision(boolean isAccessGranted) {
      allBundleOperationsAllowed = allBundleOperationsAllowed && isAccessGranted;
    }

    boolean areAllBundleOperationsAllowed() {
      return allBundleOperationsAllowed;
    }
  }
}
