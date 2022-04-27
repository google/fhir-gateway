# Copyright 2021-2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This image is for the FHIR proxy with configuration knobs as environment vars.

FROM adoptopenjdk/maven-openjdk11

WORKDIR /app

ENV PROXY_PORT=8080
ENV TOKEN_ISSUER="http://localhost/auth/realms/test"
ENV PROXY_TO="https://healthcare.googleapis.com/v1alpha2/projects/fhir-sdk/locations/us/datasets/synthea-sample-data/fhirStores/gcs-data/fhir"
ENV BACKEND_TYPE="GCP"

# If ACCESS_CHECKER is set to a non-empty value, patient level access checks
# are enabled; otherwise any valid token issued by TOKEN_ISSUER can be used
# for full access to the FHIR store.
ENV ACCESS_CHECKER="list"
ENV RUN_MODE="PROD"

COPY server/src ./server/src
COPY server/pom.xml ./server/
COPY plugins/src ./plugins/src
COPY plugins/pom.xml ./plugins/
COPY license-header.txt .
COPY pom.xml .
RUN mvn --batch-mode package -Pstandalone-app
ENTRYPOINT java -jar plugins/target/plugins-0.1.0-exec.jar \
  --server.port=${PROXY_PORT}
