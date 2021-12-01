# Files description

- `patient-list-example.json`: This is a sample list of patient IDs that can be
  uploaded to a FHIR store and used as authorization list:
  ```shell
  $ curl --request PUT \
    -H "Authorization: Bearer $(gcloud auth print-access-token)" \
    -H "Content-Type: application/fhir+json; charset=utf-8" \
    "https://healthcare.googleapis.com/v1/projects/fhir-sdk/locations/us/datasets/synthea-sample-data/fhirStores/gcs-data/fhir/List/patient-list-example" \
    -d @patient-list-example.json
  ```
  The test user that is configured on the default test Keycloak IDP has the ID
  of this list as its `patient_list` claim.
