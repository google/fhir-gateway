
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



# This is to craete a docker wrapper around an example configuration script.
# The configuration set the requirements needed for a patient list based access.

# Note the image is just for the configurator script which relies on `kcadm.sh`
# tool of Keycloak. It is not tied to `smart-keycloak` and should work for
# other Keycloak containers too. We use smart-keycloak as the base package
# for convenience.
# FROM quay.io/keycloak/keycloak
FROM quay.io/alvearie/smart-keycloak

ENV KEYCLOAK_USER="admin"
ENV KEYCLOAK_PASSWORD="adminpass"

# By default, a test realm is created for list-based access controls.
ENV TEST_REALM="test"
ENV TEST_USER="testuser"
ENV TEST_PASS="testpass"

# Setting SMART_REALM makes config changes in that realm for the sample
# Growth Chart app; this includes adding a user, client, etc. For simplicity,
# it uses the same username/password as the above realm. It is assumed that
# this realm is already created by the alvearie/keycloak-config image.
ENV SMART_REALM=""
# This should be the logical id of a Patient resource in the target FHIR store.
# The above test Keycloak user is mapped to this FHIR Patient.
ENV SMART_PATIENT_ID="8eb95e44-627f-4899-9ea3-097d4f7be57b"

COPY keycloak_setup.sh /opt/jboss/keycloak/bin/
COPY keycloak_events_list.txt /opt/jboss/keycloak/bin/

# Another option is to bring up the base Keycloak image separately then run
# the setup script in a separate `docker run`, e.g., using docker-compose.
ENTRYPOINT [ "/opt/jboss/keycloak/bin/keycloak_setup.sh" ]
