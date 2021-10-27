package com.google.fhir.proxy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class adds customizations needed for talking to a GCP FHIR store.
 */
public class GcpFhirClient extends HttpFhirClient {

  private static final Logger logger = LoggerFactory.getLogger(GcpFhirClient.class);

  private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  // The list of header names to keep in a response sent from the proxy; use lower case only.
  // Note we don't copy content-length/type because we may modify the response.
  private static final Set<String> HEADERS_TO_KEEP = Sets
      .newHashSet("last-modified", "date");

  private final GoogleCredentials credentials;
  private final String gcpFhirStore;

  public GcpFhirClient(String gcpFhirStore) throws IOException {
    credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(Collections.singleton(CLOUD_PLATFORM_SCOPE));
    this.gcpFhirStore = gcpFhirStore;
    logger.info("Initialized a client for GCP FHIR store: " + gcpFhirStore);
  }

  private String getAccessToken() {
    // TODO add support for refresh token expiration.
    try {
      credentials.refreshIfExpired();
    } catch (IOException e) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger,
          "Cannot refresh access token due to: " + e.getMessage(), e);
    }
    AccessToken accessToken = credentials.getAccessToken();
    if (accessToken == null) {
      ExceptionUtil.throwRuntimeExceptionAndLog(logger, "Cannot get an access token!");
    }
    return accessToken.getTokenValue();
  }

  @Override
  protected URI getUriForResource(String resourcePath) throws URISyntaxException {
    String uri = String.format("%s/%s", gcpFhirStore, resourcePath);
    URIBuilder uriBuilder = new URIBuilder(uri).setParameter("access_token", getAccessToken());
    return uriBuilder.build();
  }

  @Override
  public List<Header> responseHeadersToKeep(HttpResponse response) {
    List<Header> headers = Lists.newArrayList();
    for (Header header : response.getAllHeaders()) {
      if (HEADERS_TO_KEEP.contains(header.getName().toLowerCase())) {
        headers.add(header);
      }
    }
    return headers;
  }
}
