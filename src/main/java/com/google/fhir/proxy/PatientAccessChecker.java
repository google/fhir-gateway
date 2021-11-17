package com.google.fhir.proxy;

interface PatientAccessChecker {

  boolean canAccessPatient(String patientId);
}
