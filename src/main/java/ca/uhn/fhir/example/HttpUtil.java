package ca.uhn.fhir.example;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

  HttpResponse getResource(URI uri) throws IOException {
    HttpClient httpClient = HttpClients.createDefault();

    HttpUriRequest request =
        RequestBuilder.get()
            .setUri(uri)
            .addHeader("Accept-Charset", StandardCharsets.UTF_8.name())
            //.addHeader("Accept", "application/fhir+json; charset=utf-8")
            .build();

    // Execute the request and process the results.
    HttpResponse response = httpClient.execute(request);
    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
      logger.error(String.format("Error fetching FHIR resource %s; status %s",
          request.getRequestLine(), response.getStatusLine().toString()));
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          String.format("Error fetching FHIR resource %s; status %s", uri.getPath(),
              response.getStatusLine().toString()));
    }
    return response;
  }

}
