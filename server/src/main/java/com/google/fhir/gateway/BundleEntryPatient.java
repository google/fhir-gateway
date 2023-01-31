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

import com.google.common.collect.ImmutableSet;
import lombok.Getter;

@Getter
public class BundleEntryPatient {

  /** Set of patient IDs whose resources are referenced. */
  private final ImmutableSet<String> referencedPatients;

  /** Details of any modification operation on Patient resource */
  private final PatientModification patientModification;

  public BundleEntryPatient(
      ImmutableSet<String> referencedPatients, PatientModification modification) {
    this.referencedPatients = referencedPatients;
    this.patientModification = modification;
  }

  public BundleEntryPatient(ImmutableSet<String> referencedPatients) {
    this.referencedPatients = referencedPatients;
    this.patientModification = null;
  }

  @Getter
  public static class PatientModification {
    /** Set of patient IDs whose Patient resources are modified if any. */
    private final ImmutableSet<String> modifiedPatientIds;

    /** In case of modification, the operation that is being done */
    private final ModificationOperation operation;

    PatientModification(ImmutableSet<String> modifiedPatientIds, ModificationOperation operation) {
      this.modifiedPatientIds = modifiedPatientIds;
      this.operation = operation;
    }
  }

  public enum ModificationOperation {
    UPDATE,
    CREATE,
    DELETE
  }
}
