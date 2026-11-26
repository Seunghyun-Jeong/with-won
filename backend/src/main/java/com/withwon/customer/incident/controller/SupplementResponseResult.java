package com.withwon.customer.incident.controller;

import com.withwon.domain.supplement.SupplementResponse;

import java.time.LocalDateTime;

public record SupplementResponseResult(
        Long supplementResponseId,
        Long supplementRequestId,
        LocalDateTime submittedAt,
        String status
) {
    public static SupplementResponseResult from(SupplementResponse response) {
        return new SupplementResponseResult(
                response.getId(), response.getSupplementRequestId(), response.getSubmittedAt(), "SUBMITTED");
    }
}
