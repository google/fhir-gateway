#!/bin/bash

# This is for setting up a test Keycloak server. It is intended to be run as
# part of the docker container initialization; see Dockerfile's entrypoint.
#
# For documentation of the kcadm.sh tool, see:
# https://www.keycloak.org/docs/latest/server_admin/index.html#the-admin-cli
# Tip: To figure out command parameter values, we can do the required edits
# in the web-UI, then `get` the created resources to see their config.
#
# Note this is for creating a test config only; do not deploy this confiuration
# in production!
#
# Environment variables used in this setup are:
# KEYCLOAK_USER: the admin user name for Keycloak
# KEYCLOAK_PASSWORD: the password for the admin user
# FHIR_USER: the user to be created for accessing the FHIR store
# FHIR_PASS: the password of the test user

cd /opt/jboss/keycloak/bin || exit 1

success="no"
while [[ "${success}" != "yes" ]]; do
  # Login as admin first; wait until this is successful which makes sure that
  # the Keycloak server is up and running.
  sh kcadm.sh config credentials --server http://localhost:8080/auth  \
    --realm master --user ${KEYCLOAK_USER} --password ${KEYCLOAK_PASSWORD}
  if [[ $? -eq 0 ]]; then
    success="yes"
  else
    echo "ERROR: Keycloak admin authentication failed; retry after 10 seconds!"
    sleep 10
  fi
done
echo "SUCCESS; logged in with admin password!"

# Create a new `test` realm.
sh kcadm.sh create realms -s realm=test -s enabled=true -s sslRequired=none

# Enable saving of events.
sh kcadm.sh update events/config -r test -s eventsEnabled=true \
  -s adminEventsEnabled=true

# Update the list of saved events; note although the documentation says that
# with an empty list all events will be enabled, that is not ture. Namely, the
# REFRESH_* events are not enabled by default; hence this enumeration!
sh kcadm.sh update events/config -r test \
  -s 'enabledEventTypes=['"$(cat keycloak_events_list.txt)"']'

# Create a new public client with direct-grant enabled.
CID=$(sh kcadm.sh create clients -r test -s clientId=my-fhir-client \
  -s publicClient=true -s directAccessGrantsEnabled=true \
  -s redirectUris='["com.google.android.fhir.reference:/oauth2redirect","https://www.google.com"]' \
  -i)
echo "Created the new client ${CID}"

# TODO remove the group setup after all proxy uses are upgraded.
# Create a group which will be returned in `group` claim of issued tokens.
sh kcadm.sh create groups -r test -s name=fhirUser

# Add the protocol-mapper for adding `group` claim.
sh kcadm.sh create -r test clients/${CID}/protocol-mappers/models/ \
  -s name=group-fhir  -s protocolMapper=oidc-group-membership-mapper \
  -s protocol=openid-connect \
  -s config='{"full.path":"false","id.token.claim":"true","access.token.claim":"true","claim.name":"group","userinfo.token.claim":"true"}'

# Create a protocol-mapper for `patient_list` user attribute.
sh kcadm.sh create -r test clients/${CID}/protocol-mappers/models/ \
  -s name=list-mapper -s protocolMapper=oidc-usermodel-attribute-mapper \
  -s protocol=openid-connect \
  -s config='{"user.attribute":"patient_list","claim.name":"patient_list","id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true"}'

# Create the test user; set its password, group, etc.
sh kcadm.sh create users -r test -s username=${FHIR_USER} \
  -s groups='["fhirUser"]' -s enabled=true \
  -s attributes='{"patient_list":"patient-list-example"}' \
  -s credentials='[{"type":"password","value":"'${FHIR_PASS}'","temporary":false}]'
