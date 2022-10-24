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
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.fhir.proxy.HttpFhirClient;
import com.google.fhir.proxy.interfaces.AccessChecker;
import com.google.fhir.proxy.interfaces.AccessCheckerFactory;
import com.google.fhir.proxy.interfaces.AccessDecision;
import com.google.fhir.proxy.interfaces.PatientFinder;
import com.google.fhir.proxy.interfaces.RequestDetailsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

/**
 * For testing with OpenSRP Sync Access Decision.
 */
public class OpenSRPAccessTestChecker implements AccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(OpenSRPAccessTestChecker.class);


  private OpenSRPAccessTestChecker() {
  }

  /**
   * Inspects the given request to make sure that it is for a FHIR resource of a patient that the
   * current user has access too; i.e., the patient is in the patient-list associated to the user.
   *
   * @param requestDetails the original request sent to the proxy.
   * @return true iff patient is in the patient-list associated to the current user.
   */
  @Override
  public AccessDecision checkAccess(RequestDetailsReader requestDetails) {


      List<String> locationIds = new ArrayList<>();
      locationIds.add("msf");
      List<String> organisationIds = new ArrayList<>();
      organisationIds.add("P0001");
      List<String> careTeamIds = new ArrayList<>();
      return new OpenSRPSyncAccessDecision(true, locationIds, careTeamIds, organisationIds);
  }

  @Named(value = "OPENSRP_TEST_ACCESS_CHECKER")
  public static class Factory implements AccessCheckerFactory {

    @VisibleForTesting static final String PATIENT_LIST_CLAIM = "patient_list";

    @Override
    public AccessChecker create(
        DecodedJWT jwt,
        HttpFhirClient httpFhirClient,
        FhirContext fhirContext,
        PatientFinder patientFinder) {
      return new OpenSRPAccessTestChecker();
    }
  }
}
