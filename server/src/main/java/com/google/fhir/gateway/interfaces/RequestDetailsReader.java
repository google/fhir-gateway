/*
 * Copyright 2021-2025 Google LLC
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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * This is mostly a wrapper for {@link ca.uhn.fhir.rest.api.server.RequestDetails} exposing an
 * immutable subset of its API; there are minor exceptions like {@code loadRequestContents}. The
 * method names are preserved; for documentation see {@code RequestDetails}.
 */
public interface RequestDetailsReader {

  String getRequestId();

  Charset getCharset();

  String getCompleteUrl();

  FhirContext getFhirContext();

  String getFhirServerBase();

  String getHeader(String name);

  List<String> getHeaders(String name);

  IIdType getId();

  String getOperation();

  Map<String, String[]> getParameters();

  String getRequestPath();

  RequestTypeEnum getRequestType();

  String getResourceName();

  RestOperationTypeEnum getRestOperationType();

  String getSecondaryOperation();

  boolean isRespondGzip();

  byte[] loadRequestContents();

  String getServletRequestRemoteAddr();
}
