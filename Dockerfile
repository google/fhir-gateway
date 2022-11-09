#
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
#

# Image for building and running tests against the source code of
# the FHIR Access Proxy.
FROM maven:3.8.5-openjdk-11 as build

WORKDIR /app

COPY server/src ./server/src
COPY server/pom.xml ./server/
COPY plugins/src ./plugins/src
COPY plugins/pom.xml ./plugins/
COPY license-header.txt .
COPY pom.xml .

RUN mvn --batch-mode package -Dmaven.test.skip=true -Dspotless.apply.skip=true -Dspotless.check.skip=true -Pstandalone-app


# Image for FHIR Access Proxy binary with configuration knobs as environment vars.
FROM eclipse-temurin:11-jdk-focal as main

COPY --from=build /app/plugins/target/plugins-0.1.0-exec.jar /
COPY resources/hapi_page_url_allowed_queries.json resources/hapi_page_url_allowed_queries.json

ENV PROXY_PORT=8080
ENV TOKEN_ISSUER="http://localhost/auth/realms/test"
ENV PROXY_TO="http://localhost:8099/fhir"
ENV BACKEND_TYPE="HAPI"

# If ACCESS_CHECKER is set to a non-empty value, patient level access checks
# are enabled; otherwise any valid token issued by TOKEN_ISSUER can be used
# for full access to the FHIR store.
ENV ACCESS_CHECKER="list"
ENV RUN_MODE="PROD"

ENTRYPOINT java -jar plugins-0.1.0-exec.jar --server.port=${PROXY_PORT}
