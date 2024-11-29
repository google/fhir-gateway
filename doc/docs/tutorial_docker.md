# Run the Info Gateway in Docker

In this guide, you will learn how to run FHIR Info Gateway in a Docker
image, and see it work in concert with a sample Keycloak and HAPI FHIR server
running on your local machine. We assume that
[Docker](https://docs.docker.com/get-docker/) and
[Docker Compose](https://docs.docker.com/compose/) are installed. The sample
commands are shown on a Linux/shell environment and may need to be adjusted for
your environment.

!!! tip "Important"

    The setup used in this guide **should not be used in a production environment**. It is designed to get things up and running quickly for demonstration or testing purposes only. The FHIR Information Gateway Docker image might be used in a production environment if deployed appropriately, however the example access-checker plugins may not satisfy real-world use cases.

## Start the Docker images

1. Clone
   the [FHIR Info Gateway repo from GitHub](https://github.com/google/fhir-gateway).
2. Open a terminal window and `cd` to the directory where you cloned the repo.
3. Bring up the sample Keycloak service using `docker compose`.
   ```shell
   docker compose -f docker/keycloak/config-compose.yaml up
   ```
   This runs an instance of [Keycloak](https://www.keycloak.org/) with
   [SoF extension](https://github.com/Alvearie/keycloak-extensions-for-fhir),
   preloaded with a test configuration. It is accessible at
   `http://localhost:9080`.

4.  Run the sample HAPI FHIR server Docker image.
    ```shell
    docker run -p 8099:8080 us-docker.pkg.dev/fhir-proxy-build/stable/hapi-synthea:latest
    ```
    The server is preloaded with synthetic patient data and a FHIR
    `List/patient-list-example` resource.

5.  Run the FHIR Information Gateway Docker image with the `list` access
    checker.
    ```shell
    docker run \
      -e TOKEN_ISSUER=http://localhost:9080/auth/realms/test \
      -e PROXY_TO=http://localhost:8099/fhir \
      -e BACKEND_TYPE=HAPI \
      -e RUN_MODE=PROD \
      -e ACCESS_CHECKER=list \
      --network=host \
      us-docker.pkg.dev/fhir-proxy-build/stable/fhir-gateway:latest
    ```

Several environment variables are used to configure FHIR Information Gateway:

* `TOKEN_ISSUER`: The URL of the token issuer. For Keycloak this is typically
  `http://{keycloak-host}:{keycloak-port}/auth/realms/{realm-name}`.
* `PROXY_TO`: The [Service Base URL](https://build.fhir.org/http.html#root) of
  the FHIR server that FHIR Access Proxy communicates with.
* `BACKEND_TYPE`: One of `HAPI` for a HAPI FHIR Server or `GCP` for a Cloud
  Healthcare FHIR-store.
* `RUN_MODE`: One of `PROD` or `DEV`. DEV removes validation of the issuer URL,
  which is useful when using the docker image with an Android emulator as the
  emulator runs on its own virtual network and sees a different address than
  the host.
* `ACCESS_CHECKER`:  The access-checker plugin to use. The Docker image includes
  the [`list`](https://github.com/google/fhir-gateway/blob/main/plugins/src/main/java/com/google/fhir/gateway/plugin/ListAccessChecker.java)
  and [`patient`](https://github.com/google/fhir-gateway/blob/main/plugins/src/main/java/com/google/fhir/gateway/plugin/PatientAccessChecker.java)
  example access-checkers.

!!! tip "GCP Note"

    If the FHIR server is GCP FHIR-store and the gateway is not run on a VM with proper service account (e.g., running on a localhost), you need to pass GCP credentials to it, for example by mapping the `.config/gcloud` volume (i.e., add `-v ~/.config/gcloud:/root/.config/gcloud` to the above command).

## Examine the sample Keycloak configuration

In this section you will review the Keycloak settings relevant to the FHIR
Information Gateway with the sample `list` access checker plugin.

1.  Open a web browser and navigate to `http://localhost:9080/auth/admin/`.
2.  Login using user `admin` and password `adminpass`.
3.  Select the `test` realm.
4.  From the left menu, find the **Manage** section and click **Users**. Click
    **View all users**, then click the **ID** of the only result to view the
    user `Testuser`.
5.  Select the **Attributes** tab. Note the attribute `patient_list` with value
    `patient-list-example`. The client `my-fhir-client` has a corresponding
    [User Attribute
    mapper](https://www.keycloak.org/docs/latest/server_admin/#_protocol-mappers)
    to add this as a claim to the access token JWT, which you can see under
    **Clients > my-fhir-client > Mappers > list-mapper**.
6.  `patient-list-example` is the ID of a FHIR List resource which lists all the
    Patient resources the user can access. Open
    `http://localhost:8099/fhir/List/patient-list-example` to see the list
    referencing two Patients: 

    ```json
    ...
    "entry": [ {
      "item": {
        "reference": "Patient/75270"
      }
    }, {
      "item": {
        "reference": "Patient/3810"
      }
    } ]
    ...
    ```

## Get a FHIR resource using FHIR Information Gateway

1.  Get an access token for the test user. This command uses
    [jq](https://stedolan.github.io/jq/) to parse the access token from the JSON
    response.

    ```shell
    ACCESS_TOKEN="$( \
      curl -X POST \
          -d 'client_id=my-fhir-client' \
          -d 'username=testuser' \
          -d 'password=testpass' \
          -d 'grant_type=password' \
          "http://localhost:9080/auth/realms/test/protocol/openid-connect/token" \
        | jq .access_token \
        | tr -d '"' \
    )"
    ```

    You will need to rerun this command when the access token expires after 5
    minutes. In a real application, implement your Identity Provider's
    authentication flow, including refresh tokens.

2.  Send a request to FHIR Information Gateway using the access token.

    ```shell
    curl -X GET -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json; charset=utf-8" \
    'http://localhost:8080/fhir/Patient/75270'
    ```

    You should get a response containing the Patient resource.

3.  Send a second request for a patient the user does not have access to.

    ```shell
    curl -X GET -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json; charset=utf-8" \
    'http://localhost:8080/fhir/Patient/3'
    ```

    You should get a response of `User is not authorized to GET
    http://localhost:8080/fhir/Patient/3`.