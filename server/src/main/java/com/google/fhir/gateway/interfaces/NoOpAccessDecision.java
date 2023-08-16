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
package com.google.fhir.gateway.interfaces;

import org.apache.http.HttpResponse;

public final class NoOpAccessDecision implements AccessDecision {

  private final boolean accessGranted;

  public NoOpAccessDecision(boolean accessGranted) {
    this.accessGranted = accessGranted;
  }

  @Override
  public RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {
    return null;
  }

  @Override
  public boolean canAccess() {
    return accessGranted;
  }

  @Override
  public String postProcess(RequestDetailsReader requestDetailsReader, HttpResponse response) {
    return null;
  }

  public static AccessDecision accessGranted() {
    return new NoOpAccessDecision(true);
  }

  public static AccessDecision accessDenied() {
    return new NoOpAccessDecision(false);
  }
}
