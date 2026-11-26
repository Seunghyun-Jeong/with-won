package com.withwon.customer.intake.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateIncidentRequest(
        @NotEmpty List<@Valid @NotBlank String> applicationTypes
) {
}
