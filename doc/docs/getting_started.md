# Getting Started

!!! tip "Quick Start Guide"

    The easiest way to get started is to follow the ["Run the Info Gateway in Docker" guide](tutorial_docker.md).

## Building from source

To build all modules, from the root run (if you are a developer, do _not_ use
`spotless.apply.skip=true`):

```shell
mvn package -Dspotless.apply.skip=true
```

The `server` and `plugins` modules can be run together through this executable
jar (`--server.port` is just one of the many default Spring Boot flags):

```shell
java -jar exec/target/exec-0.1.0.jar --server.port=8081
```

Note that extra access-checker plugins can be added through the `loader.path`
property (although it is probably easier to build them into your server):

```shell
java -Dloader.path="PATH-TO-ADDITIONAL-PLUGINGS/custom-plugins.jar" \
  -jar exec/target/exec-0.1.0.jar --server.port=8081
```

The plugin library can be swapped with any third party access-checker as
described in the
[plugins](https://github.com/google/fhir-gateway/tree/main/plugins) directory.
Learn more about [AccessCheckers](concepts.md#access-checkers).

## Gateway to server access

The proxy must be able to send FHIR queries to the FHIR server. The FHIR server
must be configured to accept connections from the proxy while rejecting most
other requests.

If you use a
[GCP FHIR store](https://cloud.google.com/healthcare-api/docs/concepts/fhir) you
have the following options:

- If you have access to the FHIR store, you can use your own credentials by
  doing
  [application-default login](https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login).
  This is useful when testing the proxy on your local machine, and you have
  access to the FHIR server through your credentials.

- Use a service account with required access (e.g., "Healthcare FHIR Resource
  Reader", "Healthcare Dataset Viewer", "Healthcare FHIR Store Viewer"). You can
  then run the proxy in the same GCP project on a VM with this service account.

- [not-recommended] You can create and download a key file for the above service
  account, then use it with:
  ```shell
  export GOOGLE_APPLICATION_CREDENTIALS="PATH_TO_THE_JSON_KEY_FILE"
  ```

## Running the gateway

!!! tip "Configuration Parameters"

    Take a moment to review the [configuration parameters](concepts.md#configuration-parameters).

Once you have set all the above, you can run the proxy server. The sample `exec`
module uses [Apache Tomcat](https://tomcat.apache.org/) through
[Spring Boot](https://spring.io/projects/spring-boot) and the usual
configuration parameters apply, e.g., to run on port 8081:

```shell
java -jar exec/target/exec-0.1.0.jar --server.port=8081
```

!!! tip "Android Emulator"

    If the `TOKEN_ISSUER` is on the `localhost` and you are accessing the gateway from an Android emulator (which runs on a separate network), you may need to bypass proxy's token issuer check by setting `RUN_MODE=DEV` environment variable.

## Using the Info Gateway

In this section we assume that a Keycloak instance is set up using the sample
docker setup provided
[here](https://github.com/google/fhir-gateway/blob/main/docker/keycloak/config-compose.yaml).
If you have a different IDP+AuthZ setup, you need to adjust the parameters below
accordingly.

Once the gateway is running, we first need to fetch an access token from the
`TOKEN_ISSUER`; you need the test server's `username` and `password` plus the
`client_id`:

```shell
$ curl -X POST -d 'client_id=CLIENT_ID' -d 'username=testuser' \
  -d 'password=testpass' -d 'grant_type=password' \
"http://localhost:9080/auth/realms/test/protocol/openid-connect/token"
```

We need the `access_token` of the returned JSON to be able to convince the
gateway to authorize our FHIR requests (there is also a `refresh_token` in the
above response). Assuming this is stored in the `ACCESS_TOKEN` environment
variable, we can access the FHIR store:

```shell
$ curl -X GET -H "Authorization: Bearer ${ACCESS_TOKEN}" \
-H "Content-Type: application/json; charset=utf-8" \
'http://localhost:8081/Patient/f16b5191-af47-4c5a-b9ca-71e0a4365824'
```

```shell
$ curl -X PUT -H "Authorization: Bearer ${ACCESS_TOKEN}" \
-H "Content-Type: application/json; charset=utf-8" \
'http://localhost:8081/Patient/f16b5191-af47-4c5a-b9ca-71e0a4365824' \
-d @Patient_f16b5191-af47-4c5a-b9ca-71e0a4365824_modified.json
```

Of course, whether a query is accepted or denied, depends on the access-checker
used and the `ACCESS_TOKEN` claims.

For example:

- For `ACCESS_CHECKER=list` there should be a `patient_list` claim which is the
  ID of a `List` FHIR resource with all the patients that this user has access
  to.
- For `ACCESS_CHECKER=patient`, there should be a `patient_id` claim with a
  valid Patient resource ID.
