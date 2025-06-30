package com.google.fhir.gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
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

  private static final String FHIR_SERVER_BASE_URL = "http://my-fhir-server";

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

    AuditEventHelper auditEventHelper = createTestInstance(patient, RestOperationTypeEnum.CREATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent);
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

    AuditEventHelper auditEventHelper = createTestInstance(patient, RestOperationTypeEnum.READ);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent);
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

    AuditEventHelper auditEventHelper = createTestInstance(patient, RestOperationTypeEnum.UPDATE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent);
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

    AuditEventHelper auditEventHelper = createTestInstance(patient, RestOperationTypeEnum.DELETE);
    auditEventHelper.processAuditEvents();

    ArgumentCaptor<IBaseResource> payloadResourceCaptor =
        ArgumentCaptor.forClass(IBaseResource.class);
    verify(fhirClientMock).postResource(payloadResourceCaptor.capture());

    IBaseResource resource = payloadResourceCaptor.getValue();
    assertThat(resource instanceof AuditEvent, is(true));

    AuditEvent auditEvent = (AuditEvent) resource;
    assertCommonAuditEventFields(auditEvent);
    assertThat(auditEvent.getAction().toCode(), equalTo("D"));
    assertThat(auditEvent.getSubtype().get(0).getCode(), equalTo("delete"));
    assertThat(
        getAuditEventDomainResourceReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
  }

  private void assertCommonAuditEventFields(AuditEvent auditEvent) {
    assertThat(auditEvent.getOutcome().toCode(), equalTo("0"));
    assertThat(auditEvent.getOutcomeDesc(), equalTo("Success"));
    assertThat(TestUtil.isSameDate(auditEvent.getRecorded(), new Date()), is(true));
    assertThat(TestUtil.isSameDate(auditEvent.getPeriod().getStart(), new Date()), is(true));
    assertThat(TestUtil.isSameDate(auditEvent.getPeriod().getEnd(), new Date()), is(true));
    assertThat(auditEvent.getSource().getObserver().getDisplay(), equalTo(FHIR_SERVER_BASE_URL));
    assertThat(
        getAuditEventCompartmentOwnerReference(auditEvent.getEntity()),
        equalTo("Patient/test-patient-id-1"));
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
      IBaseResource payload, RestOperationTypeEnum restOperationType) {
    when(requestDetailsReader.loadRequestContents())
        .thenReturn(
            TestUtil.resourceToString(fhirContext, payload).getBytes(StandardCharsets.UTF_8));

    when(requestDetailsReader.getRestOperationType()).thenReturn(restOperationType);

    return new AuditEventHelper(
        requestDetailsReader,
        "{}",
        String.format(
            "%s/fhir/%s/%s/_history/hid-1",
            FHIR_SERVER_BASE_URL, payload.fhirType(), payload.getIdElement().getIdPart()),
        agentUserWho,
        decodedJWT,
        new Date(),
        fhirClientMock,
        fhirContext);
  }
}
