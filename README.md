# FHIR Info Gateway

<!-- Build status of the main branch -->

[![Build Status](https://storage.googleapis.com/fhir-proxy-build-badges/build.svg)](https://storage.googleapis.com/fhir-proxy-build-badges/build.html)

The FHIR Info Gateway is an access-control proxy that sits in front of a
[FHIR](https://www.hl7.org/fhir/) store (e.g., a
[HAPI FHIR](https://hapifhir.io/) server,
[GCP FHIR store](https://cloud.google.com/healthcare-api/docs/concepts/fhir),
etc.) and controls access to FHIR resources.

The authorization and access-control have three components; one of them is this
FHIR Info Gateway. The other two are an Identity Provider (IDP) and an Authorization
server (AuthZ). The responsibility of this pair is to authenticate the user and
issue access tokens (in JWT format and using authorization flow of OAuth 2.0).
The requests to the FHIR Info Gateway should have the access token as a Bearer
Authorization header. Based on that, the proxy decides whether to grant access
for a FHIR query.

![Modules involved in FHIR authorization/access-control](doc/arch_simple.png)

The initial design doc for this work is available [here](doc/design.md).

# Modules

The FHIR Info Gateway consists of a core, which is in the [server](server) module, and a set
of _access-checker_ plugins, which can be implemented by third parties and added
to the proxy server. 

* Two sample plugins are implemented in the [plugins](plugins) module. 
* There is also a sample `exec` module which shows how all pieces can be woven together into a single Spring Boot app. 

To build all modules, from the root run:

```shell
mvn package
```

The server and the plugins can be run together through this executable jar (
`--server.port` is just one of the many default Spring Boot flags):

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
described in the [plugins](plugins) directory. 

**Note** Spring Boot is not a requirement for using the access-proxy; we just use it to simplify the
[MainApp](exec/src/main/java/com/google/fhir/gateway/MainApp.java). The only
Spring-related requirement is to do a
[@ComponentScan](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/ComponentScan.html)
to find all access-checker plugins in the classpath.

# FHIR Info Gateway configuration parameters

The FHIR Info Gateway configuration parameters are currently provided through environment
variables:

- **FHIR store location**: This is set by `PROXY_TO` environment variable, using
  the base url of the FHIR store e.g.:

  ```shell
  export PROXY_TO=https://example.com/fhir
  ```

- **Access token issuer**: This is set by `TOKEN_ISSUER` variable, e.g.:

  ```shell
  export TOKEN_ISSUER=http://localhost:9080/auth/realms/test
  ```

  The above example is based on the default config of a test IDP+AuthZ
  [Keycloak](https://github.com/Alvearie/keycloak-extensions-for-fhir) server.
  
  To see how this server is configured, check the
  [docker/keycloak](docker/keycloak) directory. If you want to use a
  SMART-on-FHIR app use this realm instead:

  ```shell
  export TOKEN_ISSUER=http://localhost:9080/auth/realms/test-smart
  ```

- **AccessChecker**: As mentioned above, access-checkers can be provided as
  plugins and easily swapped. Each access-checker has a name (see
  [plugins](plugins) for details) and `ACCESS_CHECKER` variable should be set to
  this name. 
  
  For example, the two plugins that are provided in this repository, can be selected by either of:

  ```shell
  export ACCESS_CHECKER=list
  export ACCESS_CHECKER=patient
  ```
  NOTE: The above are just samples provided to demonstrate the concept of a pluggable access-checker. Custom access-checkers can be implemented to support common patterns such as use of `Location`, `Care Team` or `General Practitioner` to return the conceptual list of Patients for an authenticated user

- **AllowedQueriesChecker**: There are URL requests that the server can allow
  without going through an access checker.
  [`AllowedQueriesChecker`](https://github.com/google/fhir-access-proxy/blob/main/server/src/main/java/com/google/fhir/proxy/AllowedQueriesChecker.java)
  is a special `AccessChecker` that compares the incoming request with a
  configured set of allowed-queries. 
  
  The intended use of this checker is to override all other access-checkers for certain user-defined criteria. The user defines their criteria in a config file and if the URL query matches an entry in the config file, access is granted. An example of this is:
  [`hapi_page_url_allowed_queries.json`](https://github.com/google/fhir-access-proxy/blob/main/resources/hapi_page_url_allowed_queries.json).
  To use the file, set the `ALLOWED_QUERIES_FILE` variable:

  ```shell
  export ALLOWED_QUERIES_FILE="resources/hapi_page_url_allowed_queries.json"
  ```

- The FHIR Info Gateway makes no assumptions about what the FHIR server is, but it should be able to send any FHIR queries to the server. For example, if you use a [GCP FHIR store](https://cloud.google.com/healthcare-api/docs/concepts/fhir) you have the following options:
  - If you have access to the FHIR store, you can use your own credentials by
    doing
    [application-default login](https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login).
    This is useful when testing the proxy on your local machine, and you have
    access to the FHIR server through your credentials.
  - Use a service account with required access (e.g., "Healthcare FHIR Resource
    Reader", "Healthcare Dataset Viewer", "Healthcare FHIR Store Viewer"). You
    can then run the proxy in the same GCP project on a VM with this service
    account.
  - [not-recommended] You can create and download a key file for the above
    service account, then use it with
  ```shell
  export GOOGLE_APPLICATION_CREDENTIALS="PATH_TO_THE_JSON_KEY_FILE"
  ```

Once you have set all the above, you can run the FHIR Info Gateway server. By default, the
server uses [Apache Tomcat](https://tomcat.apache.org/) through
[Spring Boot](https://spring.io/projects/spring-boot) and the usual
configuration parameters apply, e.g., to run on port 8081:

```shell
java -jar exec/target/exec-0.1.0.jar --server.port=8081
```

## Docker

The FHIR Info Gateway is also available as a [docker image](Dockerfile):

```shell
$ docker run -p 8081:8080 -e TOKEN_ISSUER=[token_issuer_url] \
  -e PROXY_TO=[fhir_server_url] -e ACCESS_CHECKER=list \
  us-docker.pkg.dev/fhir-proxy-build/stable/fhir-access-proxy:latest
```

Note if the `TOKEN_ISSUER` is on the `localhost` you need to bypass proxy's
token issuer check by setting `RUN_MODE=DEV` environment variable.

GCP note: if this is not on a VM with proper service account (e.g., on a local
host), you need to pass GCP credentials to it, for example by mapping the
`.config/gcloud` volume (i.e., add `-v ~/.config/gcloud:/root/.config/gcloud` to
the above command).

# How to use this FHIR Info Gateway

Once the FHIR Info Gateway proxy is running, we first need to fetch an access token from the
`TOKEN_ISSUER`; you need the test `username` and `password` plus the
`client_id`:

```shell
$ curl -X POST -d 'client_id=CLIENT_ID' -d 'username=testuser' \
  -d 'password=testpass' -d 'grant_type=password' \
"http://localhost:9080/auth/realms/test/protocol/openid-connect/token"
```

We need the `access_token` of the returned JSON to be able to convince the proxy
to authorize our FHIR requests (there is also a `refresh_token` in the above
response). Assuming this is stored in the `ACCESS_TOKEN` environment variable,
we can access the FHIR store:

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
used and the `ACCESS_TOKEN` claims. For example, for `ACCESS_CHECKER=list` there
should be a `patient_list` claim which is the ID of a `List` FHIR resource with
all the patients that this user has access to. For `ACCESS_CHECKER=patient`,
there should be a `patient_id` claim with a valid Patient resource ID.

# Acknowledgement

This proxy is implemented as a
[HAPI FHIR Plain Server](https://hapifhir.io/hapi-fhir/docs/server_plain/introduction.html),
starting from this
[hapi-fhirstarters-simple-server](https://github.com/FirelyTeam/fhirstarters/tree/master/java/hapi-fhirstarters-simple-server)
example.

# Disclaimer
This is not an officially supported Google product. 

HL7®, and FHIR® are the registered trademarks of Health Level Seven International and their use of these trademarks does not constitute an endorsement by HL7.
