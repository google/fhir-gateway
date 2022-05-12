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


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    patients = ["Patient/75270", "Patient/3810"]
    resources = [("Encounter", "patient"), ("Observation", "subject")]
    auth_client = clients.AuthClient()
    fhir_proxy_client = clients.FhirProxyClient()
    hapi_client = clients.HapiClient()

    test_proxy_and_server_equal_count(
        patients, resources, hapi_client, fhir_proxy_client, auth_client
    )
    test_post_resource_increase_count(
        ("Observation", "subject"),
        "e2e-test/obs.json",
        "Patient/75270",
        hapi_client,
        fhir_proxy_client,
        auth_client,
    )
