#!/bin/bash

# First start the setup in background.
/opt/jboss/keycloak/bin/keycloak_setup.sh &

# Then starts the Keycloak server as done in the base image.
/opt/jboss/tools/docker-entrypoint.sh -b 0.0.0.0

