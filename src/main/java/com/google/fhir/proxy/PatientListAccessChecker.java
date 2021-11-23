package com.google.fhir.proxy;

import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Set;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This access-checker uses the `patient_list` ID in the access token to fetch the "List" of
 * patient IDs that the given user has access to.
 */
public class PatientListAccessChecker implements PatientAccessChecker {

  private static final Logger logger = LoggerFactory.getLogger(PatientListAccessChecker.class);
  private static final String PATIENT_LIST_CLAIM = "patient_list";
  private final Set<String> patients;

  private PatientListAccessChecker(Set<String> patients) {
    this.patients = patients;
  }

  @Override
  public boolean canAccessPatient(String patientId) {
    return patients.contains(patientId);
  }

  public static class Factory implements PatientAccessCheckerFactory {
    @Override
    public PatientAccessChecker create(DecodedJWT jwt, HttpFhirClient httpFhirClient) {
      Claim patientListClaim = jwt.getClaim(PATIENT_LIST_CLAIM);
      if (patientListClaim == null) {
        throw new ForbiddenOperationException(String.format("The provided token has no %s claim!",
            PATIENT_LIST_CLAIM));
      }
      // TODO do some sanity checks on the `patientListId`.
      String patientListId = patientListClaim.asString();
      PatientAccessChecker accessChecker = null;
      try {
        HttpResponse response = httpFhirClient.getResource(String.format("List/%s", patientListId));
        if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
          throw new ForbiddenOperationException(String.format(
              "The provided List ID '%s' in claim '%s' was not found on the FHIR server!",
              patientListId, PATIENT_LIST_CLAIM));
        }
        Set<String> patientSet = Sets.newHashSet();
        // TODO parse the returned list and fill `patientSet` (b/205977937); a better approach
        // for our immediate use-case is to use the List search functionality with patient ID.
        accessChecker = new PatientListAccessChecker(patientSet);
      } catch (IOException e) {
        ExceptionUtil.throwRuntimeExceptionAndLog(logger,
            String.format("Cannot fetch patient list %s", patientListId), e);
      }
      return accessChecker;
    }
  }
}
