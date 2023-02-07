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

import com.google.api.client.util.Lists;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import lombok.Getter;

@Getter
public class BundlePatients {

  private final ImmutableList<ImmutableSet<String>> referencedPatients;
  private final ImmutableSet<String> updatedPatients;
  private final boolean patientsToCreate;

  private final ImmutableSet<String> deletedPatients;

  private BundlePatients(
      List<ImmutableSet<String>> referencedPatients,
      Set<String> updatedPatients,
      Set<String> deletedPatients,
      boolean patientIdsToCreate) {
    this.referencedPatients = ImmutableList.copyOf(referencedPatients);
    this.updatedPatients = ImmutableSet.copyOf(updatedPatients);
    this.deletedPatients = ImmutableSet.copyOf(deletedPatients);
    this.patientsToCreate = patientIdsToCreate;
  }

  public boolean areTherePatientToCreate() {
    return patientsToCreate;
  }

  public static class BundlePatientsBuilder {
    private final Set<String> updatedPatients = Sets.newHashSet();
    private final Set<String> deletedPatients = Sets.newHashSet();
    private final List<ImmutableSet<String>> referencedPatients = Lists.newArrayList();
    private boolean patientsToCreate = false;

    public BundlePatientsBuilder addUpdatePatients(Set<String> patientsToUpdate) {
      updatedPatients.addAll(patientsToUpdate);
      return this;
    }

    public BundlePatientsBuilder addDeletedPatients(Set<String> patientsToDelete) {
      deletedPatients.addAll(patientsToDelete);
      addReferencedPatients(patientsToDelete);
      return this;
    }

    enum PatientOp {
      UPDATE,
      READ
    }

    public void addReferencedPatients(Set<String> patientIds) {
      referencedPatients.add(ImmutableSet.copyOf(patientIds));
    }

    public void addPatient(PatientOp operation, String patientId) {
      if (operation == PatientOp.READ) {
        referencedPatients.add(ImmutableSet.of(patientId));
      }

      if (operation == PatientOp.UPDATE) {
        updatedPatients.add(patientId);
      }
    }

    public void setPatientCreationFlag(boolean createPatient) {
      patientsToCreate = createPatient;
    }

    public BundlePatients build() {
      return new BundlePatients(
          referencedPatients, updatedPatients, deletedPatients, patientsToCreate);
    }
  }
}
