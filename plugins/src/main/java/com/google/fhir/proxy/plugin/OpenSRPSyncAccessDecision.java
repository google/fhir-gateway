package com.google.fhir.proxy.plugin;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.fhir.proxy.interfaces.AccessDecision;
import org.apache.http.HttpResponse;

import java.io.IOException;

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
			servletRequestDetails.setCompleteUrl(servletRequestDetails.getCompleteUrl() + getSyncTags());
		}
	}

	@Override
	public String postProcess(HttpResponse response) throws IOException {
		return accessDecision.postProcess(response);
	}

	private String getSyncTags() {

	}

	private boolean isSyncUrl(ServletRequestDetails servletRequestDetails) {
		return (servletRequestDetails.getRequestType() == RequestTypeEnum.GET && servletRequestDetails.getRestOperationType()
				.isTypeLevel());
	}
}
