package com.google.fhir.proxy;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.mockito.Mockito;

public class TestUtil {

  public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson)
      throws IOException {
    StatusLine statusLineMock = Mockito.mock(StatusLine.class);
    HttpEntity fhirEntityMock = Mockito.mock(HttpEntity.class);
    when(fhirResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(fhirResponseMock.getEntity()).thenReturn(fhirEntityMock);
    if (responseJson != null) {
      byte[] patientBytes = responseJson.getBytes(StandardCharsets.UTF_8);
      when(fhirEntityMock.getContent()).thenReturn(new ByteArrayInputStream(patientBytes));
      // Making this `lenient` since `getContentLength` is not called in all cases.
      lenient().when(fhirEntityMock.getContentLength()).thenReturn((long) patientBytes.length);
    }
  }
}
