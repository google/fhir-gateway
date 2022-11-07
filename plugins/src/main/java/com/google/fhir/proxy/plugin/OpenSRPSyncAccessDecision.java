/*
 * Copyright 2021-2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.fhir.proxy.interfaces.AccessDecision;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.util.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenSRPSyncAccessDecision implements AccessDecision {

	public static final String CARE_TEAM_TAG_URL = "http://smartregister.org/fhir/care-team-tag";

	public static final String LOCATION_TAG_URL = "http://smartregister.org/fhir/location-id";

	public static final String ORGANISATION_TAG_URL = "http://smartregister.org/organisation-tag";

	public static final String SEARCH_PARAM_TAG = "_tag";

	private String applicationId;

	private final List<String> syncStrategy;

	private boolean accessGranted;

	private List<String> careTeamIds;

	private List<String> locationIds;

	private List<String> organizationIds;

	public OpenSRPSyncAccessDecision(String applicationId, boolean accessGranted, List<String> locationIds, List<String> careTeamIds,
			List<String> organizationIds, List<String> syncStrategy) {
		this.applicationId = applicationId;
		this.accessGranted = accessGranted;
		this.careTeamIds = careTeamIds;
		this.locationIds = locationIds;
		this.organizationIds = organizationIds;
		this.syncStrategy = syncStrategy;
	}

	@Override
	public boolean canAccess() {
		return accessGranted;
	}

	@Override
	public void preProcess(ServletRequestDetails servletRequestDetails) {
		// TODO: Disable access for a user who adds tags to organisations, locations or care teams that they do not have access to
		//  This does not bar access to anyone who uses their own sync tags to circumvent
		//  the filter. The aim of this feature based on scoping was to pre-filter the data for the user
		if (isSyncUrl(servletRequestDetails)) {
			// This prevents access to a user who has no location/organisation/team assigned to them
			if (locationIds.size() == 0 && careTeamIds.size() == 0 && organizationIds.size() == 0) {
				locationIds.add(
						"CR1bAeGgaYqIpsNkG0iidfE5WVb5BJV1yltmL4YFp3o6mxj3iJPhKh4k9ROhlyZveFC8298lYzft8SIy8yMNLl5GVWQXNRr1sSeBkP2McfFZjbMYyrxlNFOJgqvtccDKKYSwBiLHq2By5tRupHcmpIIghV7Hp39KgF4iBDNqIGMKhgOIieQwt5BRih5FgnwdHrdlK9ix");
			}
			addSyncTags(servletRequestDetails, getSyncTags(locationIds, careTeamIds, organizationIds));
		}
	}

	private void addSyncTags(ServletRequestDetails servletRequestDetails, Pair<String, Map<String, String[]>> syncTags) {

		List<String> params = new ArrayList<>();

		for (Map.Entry<String, String[]> entry : syncTags.getValue().entrySet()) {
			String tagName = entry.getKey();
			for (String tagValue : entry.getValue()) {
				StringBuilder sb = new StringBuilder(tagName.length() + tagValue.length() + 2);
				sb.append(tagName);
				sb.append("|");
				sb.append(tagValue);
				params.add(sb.toString());
			}
		}

		String[] prevTagFilters = servletRequestDetails.getParameters().get(SEARCH_PARAM_TAG);
		if (prevTagFilters != null && prevTagFilters.length > 1) {
			Collections.addAll(params, prevTagFilters);
		}

		servletRequestDetails.addParameter(SEARCH_PARAM_TAG, params.toArray(new String[0]));
	}

	@Override
	public String postProcess(HttpResponse response) throws IOException {
		return null;
	}

	private Pair<String, Map<String, String[]>> getSyncTags(List<String> locationIds, List<String> careTeamIds,
			List<String> organizationIds) {
		StringBuilder sb = new StringBuilder();
		Map<String, String[]> map = new HashMap<>();

		sb.append(SEARCH_PARAM_TAG);
		sb.append("=");
		addTags(LOCATION_TAG_URL, locationIds, map, sb);
		addTags(ORGANISATION_TAG_URL, organizationIds, map, sb);
		addTags(CARE_TEAM_TAG_URL, careTeamIds, map, sb);

		return new ImmutablePair<>(sb.toString(), map);
	}

	private void addTags(String tagUrl, List<String> values, Map<String, String[]> map, StringBuilder sb) {
		int len = values.size();
		if (len > 0) {
			if (sb.length() != (SEARCH_PARAM_TAG + "=").length()) {
				sb.append(",");
			}

			map.put(tagUrl, values.toArray(new String[0]));

			int i = 0;
			for (String tagValue : values) {
				sb.append(tagUrl);
				sb.append(":");
				sb.append(tagValue);

				if (i != len - 1) {
					sb.append(",");
				}
			}
		}
	}

	private boolean isSyncUrl(ServletRequestDetails servletRequestDetails) {
		if (servletRequestDetails.getRequestType() == RequestTypeEnum.GET && !TextUtils.isEmpty(
				servletRequestDetails.getResourceName())) {
			String requestPath = servletRequestDetails.getRequestPath();
			return isResourceTypeRequest(requestPath.replace(servletRequestDetails.getFhirServerBase(), ""));
		}

		return false;
	}

	private boolean isResourceTypeRequest(String requestPath) {
		if (!TextUtils.isEmpty(requestPath)) {
			String[] sections = requestPath.split("/");

			return sections.length == 1 || (sections.length == 2 && TextUtils.isEmpty(sections[1]));
		}

		return false;
	}
}
