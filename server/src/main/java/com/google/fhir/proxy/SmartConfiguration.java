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
package com.google.fhir.proxy;

import com.google.gson.Gson;

public class SmartConfiguration {

  // TODO this is the bare minimum for a sample standalone app to work; extend this!
  private final String authorization_endpoint;
  private final String token_endpoint;
  private final String grant_types_supported = "authorization_code";
  // TODO separate SoF specific pieces and remove them if the proxy is not configured for SoF apps.
  private final String[] capabilities = {
    "launch-standalone",
    "client-public",
    "context-standalone-patient",
    "permission-patient",
    "permission-user"
  };
  // Note this is a required field but proper implementation of PKCE is tracked in b/210185844.
  private final String[] code_challenge_methods_supported = {"S256"};

  private SmartConfiguration(String tokenIssuer) {
    // TODO these have to be fetched from the authorization server config!
    authorization_endpoint = tokenIssuer + "/protocol/openid-connect/auth";
    token_endpoint = tokenIssuer + "/protocol/openid-connect/token";
  }

  public static String getConfigJson(String tokenIssuer) {
    SmartConfiguration config = new SmartConfiguration(tokenIssuer);
    Gson gson = new Gson();
    return gson.toJson(config);
  }
}
