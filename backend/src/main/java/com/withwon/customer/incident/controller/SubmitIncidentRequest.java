package com.withwon.customer.incident.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitIncidentRequest(
        @NotNull List<Long> applicationIds,
        @NotNull @Valid SubmissionConsent submissionConsent,
        @NotNull Long version
) {
    public record SubmissionConsent(
            @NotBlank String consentVersion,
            @NotNull Boolean agreed
    ) {
    }
}
