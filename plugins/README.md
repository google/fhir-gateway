# AccessChecker plugins

To implement an access-checker plugin, the
[AccessCheckerFactory interface](../server/src/main/java/com/google/fhir/gateway/interfaces/AccessCheckerFactory.java)
must be implemented, and it must be annotated by a `@Named(value = "KEY")`
annotation. `KEY` is the name of the access-checker that can be used when
running the proxy server (by setting `ACCESS_CHECKER` environment variable).

Example access-checker plugins in this module are
[ListAccessChecker](src/main/java/com/google/fhir/gateway/plugin/ListAccessChecker.java)
and
[PatientAccessChecker](src/main/java/com/google/fhir/gateway/plugin/PatientAccessChecker.java).

Beside doing basic validation of the access-token, the server also provides some
query parameters and resource parsing functionality which are wrapped inside
[PatientFinder](../server/src/main/java/com/google/fhir/gateway/interfaces/PatientFinder.java).

<!--- Add some documentation about how each access-checker works. --->
