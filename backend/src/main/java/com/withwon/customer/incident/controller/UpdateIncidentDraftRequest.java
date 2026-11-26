package com.withwon.customer.incident.controller;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateIncidentDraftRequest(
        List<String> applicationTypes,
        String incidentDescription,
        ReliefApplicationFormRequest reliefApplicationForm,
        @NotNull Long version
) {

    public UpdateIncidentDraftRequest(List<String> applicationTypes,
                                      String incidentDescription,
                                      Long version) {
        this(applicationTypes, incidentDescription, null, version);
    }
}
