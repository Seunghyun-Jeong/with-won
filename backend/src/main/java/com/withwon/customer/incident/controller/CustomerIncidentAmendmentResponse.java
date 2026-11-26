package com.withwon.customer.incident.controller;

import com.withwon.domain.amendment.AmendmentRequest;

import java.time.LocalDateTime;

public record CustomerIncidentAmendmentResponse(
        Long amendmentRequestId,
        Long applicationId,
        String requestType,
        String status,
        String reason,
        String changeData,
        String reviewReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {

    public static CustomerIncidentAmendmentResponse from(AmendmentRequest request) {
        return new CustomerIncidentAmendmentResponse(
                request.getId(),
                request.getApplication() == null ? null : request.getApplication().getId(),
                request.getRequestType(),
                request.getStatus(),
                request.getReason(),
                request.getChangeData(),
                request.getReviewReason(),
                request.getReviewedAt(),
                request.getCreatedAt());
    }
}
