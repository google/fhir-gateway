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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IIdType;

// Note instances of this class are expected to be one per thread and this class is not thread-safe
// the same way the underlying `requestDetails` is not.
public class RequestDetailsToReader implements RequestDetailsReader {
  private final RequestDetails requestDetails;

  RequestDetailsToReader(RequestDetails requestDetails) {
    this.requestDetails = requestDetails;
  }

  public String getRequestId() {
    return requestDetails.getRequestId();
  }

  public Charset getCharset() {
    return requestDetails.getCharset();
  }

  public String getCompleteUrl() {
    return requestDetails.getCompleteUrl();
  }

  public FhirContext getFhirContext() {
    // TODO: There might be a race condition in the underlying `getFhirContext`; check if this is
    // true. Note the `myServer` object is shared between threads.
    return requestDetails.getFhirContext();
  }

  public String getFhirServerBase() {
    return requestDetails.getFhirServerBase();
  }

  public String getHeader(String name) {
    return requestDetails.getHeader(name);
  }

  public List<String> getHeaders(String name) {
    return requestDetails.getHeaders(name);
  }

  public IIdType getId() {
    return requestDetails.getId();
  }

  public String getOperation() {
    return requestDetails.getOperation();
  }

  public Map<String, String[]> getParameters() {
    return requestDetails.getParameters();
  }

  public String getRequestPath() {
    return requestDetails.getRequestPath();
  }

  public RequestTypeEnum getRequestType() {
    return requestDetails.getRequestType();
  }

  public String getResourceName() {
    return requestDetails.getResourceName();
  }

  public RestOperationTypeEnum getRestOperationType() {
    return requestDetails.getRestOperationType();
  }

  public String getSecondaryOperation() {
    return requestDetails.getSecondaryOperation();
  }

  public boolean isRespondGzip() {
    return requestDetails.isRespondGzip();
  }

  public byte[] loadRequestContents() {
    return requestDetails.loadRequestContents();
  }
}
