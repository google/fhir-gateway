package com.google.fhir.gateway.plugin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.fhir.gateway.plugin.SmartFhirScope.Permission;
import com.google.fhir.gateway.plugin.SmartFhirScope.Principal;
import java.util.List;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.Test;

public class SmartScopeCheckerTest {

  @Test
  public void hasPermissionCreateObservationPatientPrincipal() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
       SmartFhirScope.extractSmartFhirScopesFromTokens(
           List.of(
               "user/Encounter.read",
               "patient/Observation.read",
               "patient/Observation.write"
           )
       ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.CREATE), equalTo(true));
  }

  @Test
  public void hasPermissionCreateObservationPatientPrincipalNoValidScope() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
        SmartFhirScope.extractSmartFhirScopesFromTokens(
            List.of(
                "user/Observation.create",
                "patient/Observation.read"
            )
        ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.CREATE), equalTo(false));
  }

  @Test
  public void hasPermissionReadObservationPatientPrincipalAllResources() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
        SmartFhirScope.extractSmartFhirScopesFromTokens(
            List.of(
                "user/*.read",
                "patient/Observation.write"
            )
        ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.READ), equalTo(false));
  }

  @Test
  public void hasPermissionDeleteObservationPatientPrincipalAllResources() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
        SmartFhirScope.extractSmartFhirScopesFromTokens(
            List.of(
                "patient/*.read",
                "patient/Observation.write"
            )
        ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.DELETE), equalTo(true));
  }

  @Test
  public void hasPermissionCreateObservationV2Scopes() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
        SmartFhirScope.extractSmartFhirScopesFromTokens(
            List.of(
                "patient/*.rs",
                "patient/Observation.u"
            )
        ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.CREATE), equalTo(false));
  }

  @Test
  public void hasPermissionDeleteObservationV2Scopes() {
    SmartScopeChecker scopeChecker = new SmartScopeChecker(
        SmartFhirScope.extractSmartFhirScopesFromTokens(
            List.of(
                "user/*.rs",
                "user/Observation.cr"
            )
        ),
        Principal.PATIENT
    );
    assertThat(scopeChecker.hasPermission(ResourceType.Observation.name(), Permission.DELETE), equalTo(false));
  }

}
