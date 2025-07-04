package com.google.fhir.gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuditEventHelperTest {

  private static final FhirContext fhirContext = FhirContext.forR4();

  @Mock private HttpFhirClient fhirClientMock;

  @Mock private DecodedJWT decodedJWT;

  @Mock private RequestDetailsReader requestDetailsReader;

  @Mock private Claim claim;

  private Reference agentUserWho;

  private static final String FHIR_SERVER_BASE_URL = "http://my-fhir-server/fhir";

  @Before
  public void setUp() {

    agentUserWho = new Reference().setReference("Practitioner/test-practitioner-id");

    when(claim.asString()).thenReturn("some-value");
    when(decodedJWT.getClaim(anyString())).thenReturn(claim);
    when(requestDetailsReader.getFhirServerBase()).thenReturn(FHIR_SERVER_BASE_URL);
  }

  @Test
  public void testProcessAuditEventsCreatePatient() throws IOException {

    Patient patient = new Patient();
    patient.setId("test-patient-id-1");
    patient.addGeneralPractitioner(agentUserWho);

    AuditEventHelper auditEventHelper =
        createTestInstance(patient, null, RestOperationTypeEnum.CREATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("C"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("create"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  @Test
  public void testProcessAuditEventReadPatient() throws IOException {

    Patient patient = new Patient();
    patient.setId("test-patient-id-1");
    patient.addGeneralPractitioner(agentUserWho);

    AuditEventHelper auditEventHelper =
        createTestInstance(patient, null, RestOperationTypeEnum.READ);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("R"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("read"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  @Test
  public void testProcessAuditEventUpdatePatient() throws IOException {

    Patient patient = new Patient();
    patient.setId("test-patient-id-1");
    patient.addGeneralPractitioner(agentUserWho);

    AuditEventHelper auditEventHelper =
        createTestInstance(patient, null, RestOperationTypeEnum.UPDATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("U"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("update"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  @Test
  public void testProcessAuditEventDeletePatient() throws IOException {

    Patient patient = new Patient();
    patient.setId("test-patient-id-1");
    patient.addGeneralPractitioner(agentUserWho);

    AuditEventHelper auditEventHelper =
        createTestInstance(patient, null, RestOperationTypeEnum.DELETE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  @Test
  public void testProcessAuditEventsCreateEncounter() throws IOException {

    Encounter encounter = new Encounter();
    encounter.setId("test-encounter-id-1");
    encounter.setSubject(new Reference("Patient/test-patient-id-1"));

    AuditEventHelper auditEventHelper =
        createTestInstance(encounter, null, RestOperationTypeEnum.CREATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("C"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("create"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Encounter/test-encounter-id-1"));
  }

  @Test
  public void testProcessAuditEventReadEncounter() throws IOException {

    Encounter encounter = new Encounter();
    encounter.setId("test-encounter-id-1");
    encounter.setSubject(new Reference("Patient/test-patient-id-1"));

    AuditEventHelper auditEventHelper =
        createTestInstance(encounter, null, RestOperationTypeEnum.READ);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("R"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("read"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Encounter/test-encounter-id-1"));
  }

  @Test
  public void testProcessAuditEventUpdateEncounter() throws IOException {

    Encounter encounter = new Encounter();
    encounter.setId("test-encounter-id-1");
    encounter.setSubject(new Reference("Patient/test-patient-id-1"));

    AuditEventHelper auditEventHelper =
        createTestInstance(encounter, null, RestOperationTypeEnum.UPDATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("U"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("update"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Encounter/test-encounter-id-1"));
  }

  @Test
  public void testProcessAuditEventDeleteEncounter() throws IOException {

    Encounter encounter = new Encounter();
    encounter.setId("test-encounter-id-1");
    encounter.setSubject(new Reference("Patient/test-patient-id-1"));

    AuditEventHelper auditEventHelper =
        createTestInstance(encounter, null, RestOperationTypeEnum.DELETE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Encounter/test-encounter-id-1"));
  }

  @Test
  public void testProcessAuditEventsCreateLocation() throws IOException {

    Location location = new Location();
    location.setId("test-location-id-1");

    AuditEventHelper auditEventHelper =
        createTestInstance(location, null, RestOperationTypeEnum.CREATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("C"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("create"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Location/test-location-id-1"));
  }

  @Test
  public void testProcessAuditEventReadLocation() throws IOException {

    Location location = new Location();
    location.setId("test-location-id-1");

    AuditEventHelper auditEventHelper =
        createTestInstance(location, null, RestOperationTypeEnum.READ);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("R"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("read"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Location/test-location-id-1"));
  }

  @Test
  public void testProcessAuditEventUpdateLocation() throws IOException {

    Location location = new Location();
    location.setId("test-location-id-1");

    AuditEventHelper auditEventHelper =
        createTestInstance(location, null, RestOperationTypeEnum.UPDATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("U"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("update"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Location/test-location-id-1"));
  }

  @Test
  public void testProcessAuditEventDeleteLocation() throws IOException {

    Location location = new Location();
    location.setId("test-location-id-1");

    AuditEventHelper auditEventHelper =
        createTestInstance(location, null, RestOperationTypeEnum.DELETE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Location/test-location-id-1"));
  }

  @Test
  public void testProcessAuditOperationError() throws IOException {

    Location location = new Location();
    location.setId("test-location-id-1");

    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.PROCESSING)
        .setDiagnostics("Deletion not allowed");

    AuditEventHelper auditEventHelper =
        createTestInstance(location, outcome, RestOperationTypeEnum.DELETE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Location/test-location-id-1"));
    assertThat(auditEvent.getOutcome().toCode(), equalTo("8"));
    assertThat(auditEvent.getOutcomeDesc(), equalTo("Deletion not allowed"));
  }

  @Test
  public void testProcessAuditEventBundlePayloadRequest() throws IOException {

    when(requestDetailsReader.getRequestType()).thenReturn(RequestTypeEnum.POST);

    // Request Bundle
    Bundle requestBundle = new Bundle();
    requestBundle.setType(Bundle.BundleType.TRANSACTION);

    String patientId = "test-patient-id-1";

    Patient patient = new Patient();
    patient.setId("test-patient-id-1");

    Bundle.BundleEntryComponent postEntry = new Bundle.BundleEntryComponent();
    postEntry.setResource(patient);
    postEntry.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");

    requestBundle.addEntry(postEntry);

    Bundle.BundleEntryComponent putEntry = new Bundle.BundleEntryComponent();
    putEntry.setResource(patient);
    putEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Patient/" + patientId);

    requestBundle.addEntry(putEntry);

    Bundle.BundleEntryComponent getEntry = new Bundle.BundleEntryComponent();
    getEntry.getRequest().setMethod(Bundle.HTTPVerb.GET).setUrl("Patient/" + patientId);

    requestBundle.addEntry(getEntry);

    Bundle.BundleEntryComponent deleteEntry = new Bundle.BundleEntryComponent();
    deleteEntry.getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("Patient/" + patientId);

    requestBundle.addEntry(deleteEntry);

    // Response Bundle
    Bundle responseBundle = new Bundle();
    responseBundle.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
    responseBundle.setId("response-bundle-1");

    Bundle.BundleEntryComponent postResponseEntry = new Bundle.BundleEntryComponent();
    Bundle.BundleEntryResponseComponent postResponse = new Bundle.BundleEntryResponseComponent();
    postResponse.setLocation("Patient/test-patient-id-1");
    postResponseEntry.setResponse(postResponse);
    responseBundle.addEntry(postResponseEntry);

    Bundle.BundleEntryComponent putResponseEntry = new Bundle.BundleEntryComponent();
    Bundle.BundleEntryResponseComponent putResponse = new Bundle.BundleEntryResponseComponent();
    putResponse.setLocation("Patient/test-patient-id-1");
    putResponseEntry.setResponse(putResponse);
    responseBundle.addEntry(putResponseEntry);

    Bundle.BundleEntryComponent getResponseEntry = new Bundle.BundleEntryComponent();
    getResponseEntry.setResource(patient);
    Bundle.BundleEntryResponseComponent getResponse = new Bundle.BundleEntryResponseComponent();
    getResponseEntry.setResponse(getResponse);
    responseBundle.addEntry(getResponseEntry);

    Bundle.BundleEntryComponent deleteResponseEntry = new Bundle.BundleEntryComponent();
    Bundle.BundleEntryResponseComponent deleteResponse = new Bundle.BundleEntryResponseComponent();
    deleteResponse.setLocation("Patient/test-patient-id-1");
    deleteResponseEntry.setResponse(deleteResponse);
    responseBundle.addEntry(deleteResponseEntry);

    // Test logic
    AuditEventHelper auditEventHelper = createTestInstance(requestBundle, responseBundle, null);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);

    verify(fhirClientMock, times(4)).postResource(payloadResourceCaptor.capture());

    List<IBaseResource> resources = payloadResourceCaptor.getAllValues();

    assertThat(resources.size(), is(4));
    assertThat(resources.get(0) instanceof AuditEvent, is(true));
    assertThat(resources.get(1) instanceof AuditEvent, is(true));
    assertThat(resources.get(2) instanceof AuditEvent, is(true));
    assertThat(resources.get(3) instanceof AuditEvent, is(true));

    AuditEvent auditEvent;

    // POST
    auditEvent = (AuditEvent) resources.get(0);
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("C"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("create"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));

    // PUT
    auditEvent = (AuditEvent) resources.get(1);
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("U"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("update"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));

    // GET
    auditEvent = (AuditEvent) resources.get(2);
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("R"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("read"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));

    // DELETE
    auditEvent = (AuditEvent) resources.get(3);
    assertCommonAuditEventFields(auditEvent, true);
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  @Test
  public void testProcessAuditEventSearchObservations() throws IOException {
    String fullRequestUrl =
        String.format(
            "%s/Observation?_id=test-observation-id-1&_id=test-observation-id-2",
            FHIR_SERVER_BASE_URL);

    when(requestDetailsReader.getFhirServerBase()).thenReturn(FHIR_SERVER_BASE_URL);
    when(requestDetailsReader.getRequestType()).thenReturn(RequestTypeEnum.GET);
    when(requestDetailsReader.getCompleteUrl()).thenReturn(fullRequestUrl);
    when(requestDetailsReader.getRestOperationType()).thenReturn(RestOperationTypeEnum.SEARCH_TYPE);
    when(requestDetailsReader.getCompleteUrl()).thenReturn(fullRequestUrl);
    when(requestDetailsReader.getRequestPath()).thenReturn("Observation");

    when(requestDetailsReader.getParameters())
        .thenReturn(Map.of("_id", new String[] {"test-observation-id-1", "test-observation-id-2"}));

    Bundle responseBundle = new Bundle();
    responseBundle.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
    responseBundle.setId("response-bundle-1");

    Bundle.BundleEntryComponent postResponseEntry = new Bundle.BundleEntryComponent();
    Bundle.BundleEntryResponseComponent postResponse = new Bundle.BundleEntryResponseComponent();
    postResponse.setLocation("Observation/test-observation-id-1");
    postResponseEntry.setResponse(postResponse);
    responseBundle.addEntry(postResponseEntry);

    Bundle.BundleEntryComponent putResponseEntry = new Bundle.BundleEntryComponent();
    Bundle.BundleEntryResponseComponent putResponse = new Bundle.BundleEntryResponseComponent();
    putResponse.setLocation("Observation/test-observation-id-2");
    putResponseEntry.setResponse(putResponse);
    responseBundle.addEntry(putResponseEntry);

    AuditEventHelper auditEventHelper =
        createTestInstance(null, responseBundle, RestOperationTypeEnum.SEARCH_TYPE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);

    verify(fhirClientMock, times(2)).postResource(payloadResourceCaptor.capture());

    List<IBaseResource> resources = payloadResourceCaptor.getAllValues();

    assertThat(resources.size(), is(2));
    assertThat(resources.get(0) instanceof AuditEvent, is(true));
    assertThat(resources.get(1) instanceof AuditEvent, is(true));

    AuditEvent auditEvent;

    // Observation 1
    auditEvent = (AuditEvent) resources.get(0);
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("E"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("search-type"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Observation/test-observation-id-1"));
    assertThat(
        getEntityQueryBySystemCode(
            auditEvent.getEntity(), "http://terminology.hl7.org/CodeSystem/object-role", "24"),
        equalTo(fullRequestUrl));

    assertThat(
        getEntityQueryDescriptionBySystemCode(
            auditEvent.getEntity(), "http://terminology.hl7.org/CodeSystem/object-role", "24"),
        equalTo("GET " + fullRequestUrl));

    // Observation 2
    auditEvent = (AuditEvent) resources.get(1);
    assertCommonAuditEventFields(auditEvent, false);
    assertThat(auditEvent.getAction().toCode(), equalTo("E"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("search-type"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Observation/test-observation-id-2"));
    assertThat(
        getEntityQueryBySystemCode(
            auditEvent.getEntity(), "http://terminology.hl7.org/CodeSystem/object-role", "24"),
        equalTo(fullRequestUrl));
    assertThat(
        getEntityQueryDescriptionBySystemCode(
            auditEvent.getEntity(), "http://terminology.hl7.org/CodeSystem/object-role", "24"),
        equalTo("GET " + fullRequestUrl));
  }

  private void assertCommonAuditEventFields(AuditEvent auditEvent, boolean inPatientCompartment) {
    assertThat(auditEvent.getOutcome().toCode(), equalTo("0"));
    assertThat(auditEvent.getOutcomeDesc(), equalTo("Success"));
    assertThat(TestUtil.isSameDate(auditEvent.getRecorded(), new Date()), is(true));
    assertThat(TestUtil.isSameDate(auditEvent.getPeriod().getStart(), new Date()), is(true));
    assertThat(TestUtil.isSameDate(auditEvent.getPeriod().getEnd(), new Date()), is(true));
    assertThat(auditEvent.getSource().getObserver().getDisplay(), equalTo(FHIR_SERVER_BASE_URL));
    assertThat(
        getAuditEventCompartmentOwnerReference(auditEvent.getEntity()),
        equalTo(inPatientCompartment ? "Patient/test-patient-id-1" : null));
    assertThat(
        getAgentReferenceBySystemCode(
            auditEvent.getAgent(),
            "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
            "IRCP"),
        equalTo("Practitioner/test-practitioner-id"));
    assertThat(
        getAgentReferenceBySystemCode(
            auditEvent.getAgent(),
            "http://dicom.nema.org/resources/ontology/DCM",
            "D".equals(auditEvent.getAction().toCode()) ? "110150" : "110153"),
        equalTo("some-value"));
  }

  private String getAuditEventCompartmentOwnerReference(
      List<AuditEvent.AuditEventEntityComponent> components) {
    return getEntityReferenceBySystemCode(
        components, "http://terminology.hl7.org/CodeSystem/object-role", "1");
  }

  private String getAuditEventDomainResourceReference(
      List<AuditEvent.AuditEventEntityComponent> components) {
    return getEntityReferenceBySystemCode(
        components, "http://terminology.hl7.org/CodeSystem/object-role", "4");
  }

  private String getEntityReferenceBySystemCode(
      List<AuditEvent.AuditEventEntityComponent> components, String system, String code) {
    for (AuditEvent.AuditEventEntityComponent component : components) {
      if (system.equals(component.getRole().getSystem())
          && code.equals(component.getRole().getCode())) {
        return component.getWhat().hasReference()
            ? component.getWhat().getReference()
            : component.getWhat().getIdentifier().getValue();
      }
    }
    return null;
  }

  private String getEntityQueryBySystemCode(
      List<AuditEvent.AuditEventEntityComponent> components, String system, String code) {
    for (AuditEvent.AuditEventEntityComponent component : components) {
      if (system.equals(component.getRole().getSystem())
          && code.equals(component.getRole().getCode())) {
        return new String(component.getQuery(), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private String getEntityQueryDescriptionBySystemCode(
      List<AuditEvent.AuditEventEntityComponent> components, String system, String code) {
    for (AuditEvent.AuditEventEntityComponent component : components) {
      if (system.equals(component.getRole().getSystem())
          && code.equals(component.getRole().getCode())) {
        return component.getDescription();
      }
    }
    return null;
  }

  private String getAgentReferenceBySystemCode(
      List<AuditEvent.AuditEventAgentComponent> components, String system, String code) {
    for (AuditEvent.AuditEventAgentComponent component : components) {
      if (system.equals(component.getType().getCodingFirstRep().getSystem())
          && code.equals(component.getType().getCodingFirstRep().getCode())) {
        return component.getWho().hasReference()
            ? component.getWho().getReference()
            : component.getWho().getIdentifier().getValue();
      }
    }
    return null;
  }

  private AuditEventHelper createTestInstance(
      @Nullable IBaseResource payload,
      @Nullable IBaseResource response,
      @Nullable RestOperationTypeEnum restOperationType) {
    when(requestDetailsReader.loadRequestContents())
        .thenReturn(
            payload != null
                ? TestUtil.resourceToString(fhirContext, payload).getBytes(StandardCharsets.UTF_8)
                : new byte[0]);

    when(requestDetailsReader.getRestOperationType()).thenReturn(restOperationType);

    return new AuditEventHelper(
        requestDetailsReader,
        response == null ? "{}" : fhirContext.newJsonParser().encodeResourceToString(response),
        String.format(
            "%s/fhir/%s/%s/_history/hid-1",
            FHIR_SERVER_BASE_URL,
            payload != null ? payload.fhirType() : "",
            payload != null && payload.getIdElement() != null
                ? payload.getIdElement().getIdPart()
                : null),
        agentUserWho,
        decodedJWT,
        new Date(),
        fhirClientMock,
        fhirContext);
  }
}
