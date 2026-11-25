package com.withwon.customer.incident.controller;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateIncidentDraftRequest(
        List<String> applicationTypes,
        String incidentDescription,
        @NotNull Long version
) {
}
