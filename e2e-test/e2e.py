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

"""End-to-end tests using the FHIR Proxy, HAPI Server, and AuthZ Server."""

import logging
import time
from typing import List, Tuple

import clients
import os

from unittest.mock import patch


def test_proxy_and_server_equal_count(
    patient_list: List[str],
    resource_search_pairs: List[Tuple[str, str]],
    hapi: clients.HapiClient,
    fhir_proxy: clients.FhirProxyClient,
    auth: clients.AuthClient,
) -> None:
    """Checks number of resources are the same via the Proxy or HAPI."""
    token = auth.get_auth_token()
    for patient in patient_list:
        for resource_search_pair in resource_search_pairs:
            value_from_server = hapi.get_resource_count(
                resource_search_pair[0], patient
            )
            value_from_proxy = fhir_proxy.get_resource_count(
                token, resource_search_pair, patient
            )
            logging.info(
                "%s resources returned for %s from: \n\tServer: %s, Proxy: %s",
                resource_search_pair[0],
                patient,
                value_from_server,
                value_from_proxy,
            )

            if value_from_server != value_from_proxy:
                error_msg = "Number of resources do not match\n\tServer: {}, Proxy: {}".format(
                    value_from_server, value_from_proxy
                )
                raise ValueError(error_msg)


def test_post_resource_increase_count(
    resource_search_pair: Tuple[str, str],
    file_name: str,
    patient_id: str,
    hapi: clients.HapiClient,
    fhir_proxy: clients.FhirProxyClient,
    auth: clients.AuthClient,
) -> None:
    """Test to add a resource to the backend via the Proxy."""
    token = auth_client.get_auth_token()
    value_from_server = hapi.get_resource_count(resource_search_pair[0], patient_id)
    value_from_proxy = fhir_proxy.get_resource_count(
        token, resource_search_pair, patient_id
    )

    if value_from_server != value_from_proxy:
        error_msg = "Number of resources do not match\n\tServer: {}, Proxy: {}".format(
            value_from_server, value_from_proxy
        )
        raise ValueError(error_msg)

    current_value = value_from_proxy
    logging.info(
        "%s %ss returned for %s", current_value, resource_search_pair[0], patient_id,
    )

    logging.info("Adding one %s for %s", resource_search_pair[0], patient_id)
    fhir_proxy.post_resource(resource_search_pair[0], file_name, token)

    while value_from_proxy != (current_value + 1):
        token = auth.get_auth_token()
        value_from_proxy = fhir_proxy.get_resource_count(
            token, resource_search_pair, patient_id
        )
        time.sleep(10)

    logging.info("Added one %s for %s", resource_search_pair[0], patient_id)
    logging.info(
        "%s %ss returned for %s", value_from_proxy, resource_search_pair[0], patient_id,
    )

@patch.dict(os.environ, {"AUDIT_EVENT_LOGGING_ENABLED_ENV": "true"})
def test_post_resource_with_logging_enabled_creates_audit_event(
    file_name: str,
    hapi: clients.HapiClient,
    fhir_proxy: clients.FhirProxyClient,
    auth: clients.AuthClient,
) -> None:
    """Test to add a resource to the backend via the Proxy and verify AuditEvent creation."""
    token = auth.get_auth_token()
 
    initial_audit_event_count = hapi.get_audit_event_count()
    logging.info("Initial AuditEvent count: %d", initial_audit_event_count)

    fhir_proxy.post_resource("Observation", file_name, token)
    logging.info("Posted Observation resource via Proxy.")

    max_wait_seconds = 60
    poll_interval = 5
    waited = 0
    while waited < max_wait_seconds:
        current_audit_event_count = hapi.get_audit_event_count()
        if current_audit_event_count == initial_audit_event_count + 1:
            logging.info("AuditEvent created successfully after POST.")
            return
        time.sleep(poll_interval)
        waited += poll_interval
        logging.info("Waiting for AuditEvent... (%ds elapsed)", waited)

    raise AssertionError(
        "AuditEvent was not created after POST within {} seconds." 
        "Initial count: {}, Current count: {}".format(max_wait_seconds, initial_audit_event_count, current_audit_event_count)
    )

@patch.dict(os.environ, {"AUDIT_EVENT_LOGGING_ENABLED_ENV": "true"})
def test_post_bundle_with_logging_enabled_creates_audit_events(
    file_name: str,
    hapi: clients.HapiClient,
    fhir_proxy: clients.FhirProxyClient,
    auth: clients.AuthClient,
) -> None:
    """Test to add a resource to the backend via the Proxy and verify AuditEvent creation."""
    token = auth.get_auth_token()
 
    initial_audit_event_count = hapi.get_audit_event_count()
    logging.info("Initial AuditEvent count: %d", initial_audit_event_count)

    fhir_proxy.post_resource("", file_name, token)
    logging.info("Posted Bundle resource via Proxy.")

    max_wait_seconds =  60
    poll_interval = 5
    waited = 0
    while waited < max_wait_seconds:
        current_audit_event_count = hapi.get_audit_event_count()
        if current_audit_event_count == initial_audit_event_count + 2:
            logging.info("AuditEvent created successfully after POST.")
            return
        time.sleep(poll_interval)
        waited += poll_interval
        logging.info("Waiting for AuditEvent... (%ds elapsed)", waited)

    raise AssertionError(
        "AuditEvent was not created after POST within {} seconds." 
        "Initial count: {}, Current count: {}".format(max_wait_seconds, initial_audit_event_count, current_audit_event_count)
    )

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    patients = ["Patient/75270", "Patient/3810"]
    resources = [("Encounter", "patient"), ("Observation", "subject")]
    auth_client = clients.AuthClient()
    fhir_proxy_client = clients.FhirProxyClient()
    hapi_client = clients.HapiClient()

    logging.info("Testing proxy and server resource counts ...")
    test_proxy_and_server_equal_count(
        patients, resources, hapi_client, fhir_proxy_client, auth_client
    )
    logging.info("Testing post resource ...")
    test_post_resource_increase_count(
        ("Observation", "subject"),
        "e2e-test/obs.json",
        "Patient/75270",
        hapi_client,
        fhir_proxy_client,
        auth_client,
    )
   
    logging.info("Testing post resource with AuditEvent logging enabled")
    test_post_resource_with_logging_enabled_creates_audit_event(
        "e2e-test/obs.json",
        hapi_client,
        fhir_proxy_client,
        auth_client,
    )
   
    logging.info("Testing post bundle with AuditEvent logging enabled")
    test_post_bundle_with_logging_enabled_creates_audit_events(
        "e2e-test/bundle.json",
        hapi_client,
        fhir_proxy_client,
        auth_client,
    )

