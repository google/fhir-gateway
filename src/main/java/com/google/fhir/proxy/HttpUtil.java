package com.google.fhir.proxy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtil {

  private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

  static void validateResponseOrFail(HttpResponse response, String resource) {
    validateResponseOrFail(response, resource, false);
  }

  static void validateResponseEntityOrFail(HttpResponse response, String resource) {
    validateResponseOrFail(response, resource, true);
  }

  private static void validateResponseOrFail(HttpResponse response, String resource,
      boolean checkEntity) {
    boolean isValid = (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
    if (checkEntity) {
      isValid = isValid && (response.getEntity() != null);
    }
    if (!isValid) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          String.format("Error accessing resource %s; status %s",
              resource, response.getStatusLine().toString()));
    }
  }

  HttpResponse getResourceOrFail(URI uri) throws IOException {
    HttpClient httpClient = HttpClients.createDefault();
    HttpUriRequest request =
        RequestBuilder.get()
            .setUri(uri)
            .addHeader("Accept-Charset", StandardCharsets.UTF_8.name())
            //.addHeader("Accept", "application/fhir+json; charset=utf-8")
            .build();
    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(request);
    validateResponseOrFail(response, uri.toString());
    return response;
  }

}
