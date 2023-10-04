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

import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;

/**
 * This is an example servlet that can be used for any custom endpoint. It does not make any
 * assumptions about authorization headers or accessing a FHIR server.
 */
@WebServlet("/custom/*")
public class CustomGenericEndpointExample extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse resp) throws IOException {
    String uri = request.getRequestURI();
    // For a real production case, `uri` needs to be escaped.
    resp.getOutputStream().print("Successful request to the custom endpoint " + uri);
    resp.setStatus(HttpStatus.SC_OK);
  }
}
