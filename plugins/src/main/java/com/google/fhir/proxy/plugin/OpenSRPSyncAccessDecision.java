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

import com.google.fhir.proxy.interfaces.AccessDecision;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpResponse;

public class OpenSRPSyncAccessDecision implements AccessDecision {

  private String applicationId;
  private List<String> careTeamIds;
  private List<String> locationIds;
  private List<String> organizationIds;
  private List<String> syncStrategy;

  public OpenSRPSyncAccessDecision(
      String applicationId,
      List<String> careTeamIds,
      List<String> locationIds,
      List<String> organizationIds,
      List<String> syncStrategy) {
    this.applicationId = applicationId;
    this.careTeamIds = careTeamIds;
    this.locationIds = locationIds;
    this.organizationIds = organizationIds;
    this.syncStrategy = syncStrategy;
  }

  @Override
  public boolean canAccess() {
    return true;
  }

  @Override
  public String postProcess(HttpResponse response) throws IOException {
    return null;
  }
}
