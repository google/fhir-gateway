# Description of resource files

- `CompartmentDefinition-patient.json`: This is from the FHIR specification. It
  can be fetched on-the-fly when the proxy runs. However, given the importance
  of this resource and to make things simpler, it is downloaded and made
  available statically:

  ```shell
  $ curl -X GET -L -H "Accept: application/fhir+json" \
    http://hl7.org/fhir/CompartmentDefinition/patient \
    -o CompartmentDefinition-patient.json
  ```

  **NOTE**: We have also made changes to this file due to
  [b/215051963](b/215051963).

- `patient_paths.json`: For each FHIR resource, this file has the corresponding
  mapping of the list of FHIR paths that should be searched for finding patients
  in that resource. This is used for access control of POST and PUT requests
  where a resource is provided by the client (see [b/209207333](b/209207333)).

- `logback.xml`: The Logback configuration
