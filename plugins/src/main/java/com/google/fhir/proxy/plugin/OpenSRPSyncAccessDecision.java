package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.fhir.proxy.interfaces.AccessDecision;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.util.Map;

public class OpenSRPSyncAccessDecision implements AccessDecision {

	private AccessDecision accessDecision;

	public OpenSRPSyncAccessDecision(AccessDecision accessDecision) {
		this.accessDecision = accessDecision;
	}

	@Override
	public boolean canAccess() {
		return accessDecision.canAccess();
	}

	@Override
	public void preProcess(ServletRequestDetails servletRequestDetails) {
		if (isSyncUrl(servletRequestDetails)) {
			addSyncTags(servletRequestDetails, getSyncTags());
		}
	}

	private void addSyncTags(ServletRequestDetails servletRequestDetails, Pair<String, Map<String, String[]>> syncTags) {
		String syncTagsString = getSyncTags().getKey();
		if (servletRequestDetails.getParameters().size() == 0) {
			syncTagsString = "?" + syncTagsString;
		}

		servletRequestDetails.setCompleteUrl(servletRequestDetails.getCompleteUrl() + syncTagsString);
		servletRequestDetails.setRequestPath(servletRequestDetails.getRequestPath() + syncTagsString);

		for (Map.Entry<String, String[]> entry: syncTags.getValue().entrySet()) {
			servletRequestDetails.addParameter(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public String postProcess(HttpResponse response) throws IOException {
		return accessDecision.postProcess(response);
	}

	private Pair<String, Map<String, String[]>> getSyncTags() {

	}

	private boolean isSyncUrl(ServletRequestDetails servletRequestDetails) {
		return (servletRequestDetails.getRequestType() == RequestTypeEnum.GET && servletRequestDetails.getRestOperationType()
				.isTypeLevel());
	}
}
