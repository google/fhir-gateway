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

- `logback.xml`: The Logback configuration

