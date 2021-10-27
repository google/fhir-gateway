package ca.uhn.fhir.example;

import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpFhirClient.class);

  protected abstract URI getUriForResource(String resourcePath) throws URISyntaxException;

  public abstract List<Header> responseHeadersToKeep(HttpResponse response);

  HttpResponse handleRequest(ServletRequestDetails request) throws IOException {
    // TODO reuse if creation overhead is significant.
    HttpClient httpClient = HttpClients.createDefault();

    String httpMethod = request.getServletRequest().getMethod();
    RequestBuilder builder = RequestBuilder.create(httpMethod);
    try {
      URI uri = getUriForResource(request.getRequestPath());
      builder.setUri(uri);
      logger.info("FHIR store resource is " + uri);
    } catch (URISyntaxException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          "Error in build URI for resource " + request.getRequestPath());
    }
    // TODO Check why this does not work Content-Type is application/x-www-form-urlencoded.
    byte[] requestContent = request.loadRequestContents();
    if (requestContent != null && requestContent.length > 0) {
      String contentType = request.getHeader("Content-Type");
      if (contentType == null) {
        ExceptionUtil.throwRuntimeExceptionAndLog(logger,
            "Content-Type header should be set for requests with body.");
      }
      builder.setEntity(new ByteArrayEntity(requestContent));
    }
    copyRequiredHeaders(request, builder);
    copyParameters(request, builder);
    HttpUriRequest httpRequest = builder.build();
    logger.info("Request to the FHIR store is " + httpRequest);

    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(httpRequest);
    if (response.getStatusLine().getStatusCode() >= 400) {
      logger.error(String.format("Error in FHIR resource %s method %s; status %s",
          httpRequest.getRequestLine(), httpMethod, response.getStatusLine().toString()));
    }
    return response;
  }

  @VisibleForTesting
  void copyRequiredHeaders(ServletRequestDetails request, RequestBuilder builder) {
    // We should NOT copy Content-Length as this is automatically set by the RequestBuilder when
    // setting content Entity; otherwise we will get a ClientProtocolException.
    Set<String> requiredHeaders = Sets
        .newHashSet("content-type");
    for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
      if (requiredHeaders.contains(entry.getKey().toLowerCase())) {
        for (String value : entry.getValue()) {
          builder.setHeader(entry.getKey(), value);
        }
      }
    }
  }

  @VisibleForTesting
  void copyParameters(ServletRequestDetails request, RequestBuilder builder) {
    // TODO Check if we can directly do this by copying request.getServletRequest().getQueryString().
    for (Map.Entry<String, String[]> entry : request.getParameters().entrySet()) {
      for (String val : entry.getValue()) {
        builder.addParameter(entry.getKey(), val);
      }
    }
  }

}
