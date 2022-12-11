#!/bin/bash
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

# Fail on any error.
set -e

export BUILD_ID=${KOKORO_BUILD_ID:-local}

function setup() {
  docker build -t us-docker.pkg.dev/fhir-proxy-build/stable/fhir-access-proxy:${BUILD_ID} .
  docker-compose -f docker/keycloak/config-compose.yaml \
                 up --force-recreate --remove-orphans -d --quiet-pull
  # TODO find a way to expose docker container logs in the output; currently
  #   with -d we don't get any logs and it makes debugging failures difficult.
  # TODO what does `--wait` do? It does not seem to be a documented option and
  #   fails some versions of docker-compose.
  docker-compose -f docker/hapi-proxy-compose.yaml \
                 up --force-recreate --remove-orphans -d --quiet-pull
  # Making sure that all services are up
  # TODO this is a hack; we need to make the downstream clients more resilient.
  echo "Containers status before wait:"
  docker ps
  sleep 120
  echo "Containers status after wait:"
  docker ps
}

setup
python3 e2e-test/e2e.py
