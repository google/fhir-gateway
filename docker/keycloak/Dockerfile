# This is to add a setup script to the base Keycloak image then modify the
# entrypoint to run that script in addition to running the server.

FROM quay.io/keycloak/keycloak

# Use the default H2 to prevent issues when Keycloak tries to guess other DBs!
ENV DB_VENDOR="H2"
ENV KEYCLOAK_USER="admin"
ENV KEYCLOAK_PASSWORD="adminpass"
ENV FHIR_USER="test"
ENV FHIR_USER_PASS="testpass"

COPY keycloak_setup.sh /opt/jboss/keycloak/bin/
COPY keycloak_events_list.txt /opt/jboss/keycloak/bin/
COPY run.sh /opt/jboss/keycloak/bin/

# Another option is to bring up the base Keycloak image separately then run
# the setup script in a separate `docker run`, e.g., using docker-compose.
ENTRYPOINT [ "/opt/jboss/keycloak/bin/run.sh" ]
