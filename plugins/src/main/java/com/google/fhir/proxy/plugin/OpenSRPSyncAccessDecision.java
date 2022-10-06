package com.google.fhir.proxy.plugin;

import com.google.fhir.proxy.interfaces.AccessDecision;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.util.List;

public class OpenSRPSyncAccessDecision implements AccessDecision {

    private String applicationId;
    private List<String> careTeamIds;
    private List<String> locationIds;
    private List<String> organizationIds;
    private List<String> syncStrategy;

    public OpenSRPSyncAccessDecision(String applicationId, List<String> careTeamIds, List<String> locationIds, List<String> organizationIds, List<String> syncStrategy) {
        this.applicationId = applicationId;
        this.careTeamIds = careTeamIds;
        this.locationIds = locationIds;
        this.organizationIds = organizationIds;
        this.syncStrategy = syncStrategy;
    }

    @Override
    public boolean canAccess() {
        return true;
    }

    @Override
    public String postProcess(HttpResponse response) throws IOException {
        return null;
    }
}
