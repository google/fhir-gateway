# This image is for the FHIR proxy with configuration knobs as environment vars.

FROM adoptopenjdk/maven-openjdk8

WORKDIR /app

ENV PROXY_PORT=8080
ENV TOKEN_ISSUER="http://35-224-71-179.nip.io/auth/realms/test"
ENV PROXY_TO="https://healthcare.googleapis.com/v1/projects/fhir-sdk/locations/us/datasets/synthea-sample-data/fhirStores/gcs-data/fhir"

# If ACCESS_CHECKER is set to a non-empty value, patient level access checks
# are enabled; otherwise any valid token issued by TOKEN_ISSUER can be used
# for full access to the FHIR store.
ENV ACCESS_CHECKER=""
ENV RUN_MODE="DEV"

COPY src ./src
COPY pom.xml .
RUN mvn package
# TODO: Make a standalone java app with the web container (jetty) embedded.
ENTRYPOINT mvn jetty:run -Djetty.http.port=${PROXY_PORT} -Djetty.host=0.0.0.0
