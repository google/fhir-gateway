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

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.common.collect.ImmutableSet;
import com.google.fhir.gateway.interfaces.BundleProcessingWorker;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker that implements the {@link BundleProcessingWorker}. The worker provides logic to process
 * each entry in the Bundle and extract all patient IDs from each entry This class also provides a
 * method to retrieve {@link BundlePatients} as a result of processing all the entries in the Bundle
 */
public class BundlePatientFinder implements BundleProcessingWorker {

  private PatientFinder patientFinder;

  private RequestDetailsReader requestDetailsReader;

  private BundleProcessorUtils bundleProcessorUtils;

  private BundlePatients.BundlePatientsBuilder bundlePatientsBuilder;

  public BundlePatientFinder(
      RequestDetailsReader requestDetailsReader,
      PatientFinder patientFinder,
      BundleProcessorUtils bundleProcessorUtils) {
    this.patientFinder = patientFinder;
    this.bundleProcessorUtils = bundleProcessorUtils;
    this.requestDetailsReader = requestDetailsReader;
    this.bundlePatientsBuilder = new BundlePatients.BundlePatientsBuilder();
  }

  private static final Logger logger = LoggerFactory.getLogger(PatientFinderImp.class);

  /**
   * Find all patients referenced or updated in a Bundle.
   *
   * @return the {@link BundlePatients} that wraps all found patients.
   * @throws InvalidRequestException for various reasons when unexpected content is encountered.
   *     Callers are expected to deny access when this happens.
   */
  public BundlePatients findPatientsInBundle() {
    this.bundleProcessorUtils.processBundleFromRequest(this.requestDetailsReader, this);
    return this.bundlePatientsBuilder.build();
  }

  @Override
  public void processBundleEntryComponent(BundleEntryComponent bundleEntryComponent) {
    processPatientBundleEntryInBundle(processBundleEntryComponentForPatients(bundleEntryComponent));
  }

  public BundleEntryPatient processBundleEntryComponentForPatients(
      BundleEntryComponent bundleEntryComponent) {
    HTTPVerb httpMethod = bundleEntryComponent.getRequest().getMethod();
    if (httpMethod != HTTPVerb.GET
        && httpMethod != HTTPVerb.DELETE
        && !bundleEntryComponent.hasResource()) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Bundle entry requires a resource field!", InvalidRequestException.class);
    }

    BundleEntryPatient bundleEntryPatient = null;
    switch (httpMethod) {
      case GET:
        bundleEntryPatient = processGetBundleEntry(bundleEntryComponent);
        break;
      case DELETE:
        bundleEntryPatient = processDeleteBundleEntry(bundleEntryComponent);
        break;
      case POST:
        bundleEntryPatient = processPostBundleEntry(bundleEntryComponent);
        break;
      case PATCH:
        bundleEntryPatient = processPatchBundleEntry(bundleEntryComponent);
        break;
      case PUT:
        bundleEntryPatient = processPutBundleEntry(bundleEntryComponent);
        break;
    }
    return bundleEntryPatient;
  }

  @Override
  public boolean processNextBundleEntry() {
    return true;
  }

  private BundleEntryPatient processGetBundleEntry(BundleEntryComponent entryComponent) {
    // Ignore body content and just look at request.
    try {
      String patientId = findPatientId(entryComponent.getRequest());
      return new BundleEntryPatient(ImmutableSet.of(patientId));
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error parsing URI in Bundle!", e, InvalidRequestException.class);
    }
    return null;
  }

  private void processPatientBundleEntryInBundle(BundleEntryPatient patientEntry) {
    if (patientEntry == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Unsupported request", InvalidRequestException.class);
    }
    if (!patientEntry.getReferencedPatients().isEmpty()) {
      this.bundlePatientsBuilder.addReferencedPatients(patientEntry.getReferencedPatients());
    }
    if (patientEntry.getPatientModification() != null) {
      switch (patientEntry.getPatientModification().getOperation()) {
        case CREATE:
          this.bundlePatientsBuilder.setPatientCreationFlag(true);
          break;
        case DELETE:
          this.bundlePatientsBuilder.addDeletedPatients(
              patientEntry.getPatientModification().getModifiedPatientIds());
          break;
        case UPDATE:
          this.bundlePatientsBuilder.addUpdatePatients(
              patientEntry.getPatientModification().getModifiedPatientIds());
          break;
      }
    }
  }

  private BundleEntryPatient processDeleteBundleEntry(BundleEntryComponent entryComponent) {
    // Ignore body content and just look at request.
    try {
      String patientId = findPatientId(entryComponent.getRequest());
      if (isPatientResourceType(entryComponent.getRequest())) {
        return new BundleEntryPatient(
            ImmutableSet.of(patientId),
            new BundleEntryPatient.PatientModification(
                ImmutableSet.of(patientId), BundleEntryPatient.ModificationOperation.DELETE));
      } else {
        return new BundleEntryPatient(ImmutableSet.of(patientId));
      }
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error parsing URI in Bundle!", e, InvalidRequestException.class);
    }
    return null;
  }

  private BundleEntryPatient processPatchBundleEntry(BundleEntryComponent entryComponent) {
    // Find patient id in request.url
    Set<String> referencedPatients = new HashSet<>();
    BundleEntryPatient.PatientModification modification = null;
    try {
      String patientId = findPatientId(entryComponent.getRequest());
      String resourceType =
          new Reference(entryComponent.getResource().getId())
              .getReferenceElement()
              .getResourceType();
      if (FhirUtil.isSameResourceType(resourceType, ResourceType.Patient)) {
        modification =
            new BundleEntryPatient.PatientModification(
                ImmutableSet.of(patientId), BundleEntryPatient.ModificationOperation.UPDATE);
      } else {
        referencedPatients.add(patientId);
      }
      // Find patient ids in body
      if (!FhirUtil.isSameResourceType(
          entryComponent.getResource().fhirType(), ResourceType.Binary)) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "PATCH resource type must be Binary", InvalidRequestException.class);
      }

      Binary binaryResource = (Binary) entryComponent.getResource();
      if (!binaryResource.getContentType().equals(Constants.CT_JSON_PATCH)) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger,
            String.format("PATCH content type must be %s", Constants.CT_JSON_PATCH),
            InvalidRequestException.class);
      }

      JsonArray jsonArray =
          JsonParser.parseString(new String(binaryResource.getData())).getAsJsonArray();
      Set<String> patientsInPatch = patientFinder.findPatientsInPatchArray(jsonArray, resourceType);
      if (!patientsInPatch.isEmpty()) {
        referencedPatients.addAll(patientsInPatch);
      }
      if (modification != null) {
        return new BundleEntryPatient(ImmutableSet.copyOf(referencedPatients), modification);
      } else {
        return new BundleEntryPatient(ImmutableSet.copyOf(referencedPatients));
      }
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error parsing URI in Bundle!", e, InvalidRequestException.class);
    }
    return null;
  }

  private BundleEntryPatient processPostBundleEntry(BundleEntryComponent entryComponent) {
    Resource resource = entryComponent.getResource();
    if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
      return new BundleEntryPatient(
          ImmutableSet.of(),
          new BundleEntryPatient.PatientModification(
              ImmutableSet.of(), BundleEntryPatient.ModificationOperation.CREATE));
    } else {
      return new BundleEntryPatient(
          ImmutableSet.copyOf(getPatientReference(resource, resource.fhirType())));
    }
  }

  private BundleEntryPatient processPutBundleEntry(BundleEntryComponent entryComponent) {
    Resource resource = entryComponent.getResource();
    try {
      String patientId = findPatientId(entryComponent.getRequest());
      if (FhirUtil.isSameResourceType(resource.fhirType(), ResourceType.Patient)) {
        return new BundleEntryPatient(
            ImmutableSet.of(),
            new BundleEntryPatient.PatientModification(
                ImmutableSet.of(patientId), BundleEntryPatient.ModificationOperation.UPDATE));
      } else {
        Set<String> referencedPatients = new HashSet<>();
        referencedPatients.add(patientId);
        referencedPatients.addAll(getPatientReference(resource, resource.fhirType()));
        return new BundleEntryPatient(ImmutableSet.copyOf(referencedPatients));
      }
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Error parsing URI in Bundle!", e, InvalidRequestException.class);
    }
    return null;
  }

  /** Checks if the request is for a Patient resource. */
  private Boolean isPatientResourceType(Bundle.BundleEntryRequestComponent requestComponent)
      throws URISyntaxException {
    if (requestComponent.getUrl() != null) {
      URI resourceUri = new URI(requestComponent.getUrl());
      IIdType referenceElement = new Reference(resourceUri.getPath()).getReferenceElement();
      return FhirUtil.isSameResourceType(referenceElement.getResourceType(), ResourceType.Patient);
    }
    return false;
  }

  private String findPatientId(Bundle.BundleEntryRequestComponent requestComponent)
      throws URISyntaxException {
    if (requestComponent.getUrl() == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger,
          "No request URL  found for bundle entry" + requestComponent.getId(),
          InvalidRequestException.class);
    }
    URI resourceUri = new URI(requestComponent.getUrl());
    return patientFinder.findPatientFromUrl(
        new BundleEntryRequestUrlDetailsFinder(requestComponent));
  }

  private Set<String> getPatientReference(Resource resource, String resourceName) {
    Set<String> referencePatientIds = patientFinder.findPatientsInResource(resource, resourceName);
    if (referencePatientIds.isEmpty()) {
      ExceptionUtil.throwRuntimeExceptionAndLog(
          logger, "Patient reference must exist in resource", InvalidRequestException.class);
    }
    return referencePatientIds;
  }
}
