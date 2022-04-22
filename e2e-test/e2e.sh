#!/bin/bash
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

# Fail on any error.
set -e

export BUILD_ID=${KOKORO_BUILD_ID:-local}

function setup() {
  docker build -t gcr.io/fhir-sdk/fhir-proxy:${BUILD_ID} .
  docker-compose -f docker/keycloak/config-compose.yaml \
                 up --force-recreate --remove-orphans -d
  docker-compose -f docker/hapi-proxy-compose.yaml \
                 up --force-recreate --remove-orphans -d
}

function wait_for_start() {
  local sink_server='http://localhost:8099'
  local num_retries=0
  local status_code=500
  until [[ ${status_code} -eq 200 ]]; do
    echo "WAITING FOR FHIR SERVER TO START"
    sleep 10s
    status_code=$(curl -o /dev/null --head -w "%{http_code}" -L -X GET \
      --connect-timeout 5 --max-time 20 \
      ${sink_server}/fhir/Observation 2>/dev/null) || status_code=500
    ((num_retries += 1))
    if [[ num_retries == 18 ]]; then
      echo "TERMINATING AS FHIR SERVER TOOK TOO LONG TO START"
      exit 1
    fi
  done
  echo "FHIR SERVER STARTED SUCCESSFULLY"
}

setup
wait_for_start
python3 e2e-test/e2e.py
