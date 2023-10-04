# Sample application

This module is to show simple examples of how to use the FHIR Gateway. The
minimal application is
[MainApp](src/main/java/com/google/fhir/gateway/MainApp.java). With this single
class, you can create an executable app with the Gateway [server](../server) and
all of the `AccessChecker` [plugins](../plugins), namely
[ListAccessChecker](../plugins/src/main/java/com/google/fhir/gateway/plugin/ListAccessChecker.java)
and
[PatientAccessChecker](../plugins/src/main/java/com/google/fhir/gateway/plugin/PatientAccessChecker.java).

Two other classes are provided to show how to implement custom endpoints.
