package com.withwon.customer.incident.controller;

import com.withwon.domain.supplement.SupplementRequest;

import java.time.LocalDateTime;

public record CustomerIncidentSupplementResponse(
        Long supplementRequestId,
        boolean refundTarget,
        boolean compensationTarget,
        String message,
        String requiredItems,
        String status,
        LocalDateTime dueAt,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {

    public static CustomerIncidentSupplementResponse from(SupplementRequest request) {
        return new CustomerIncidentSupplementResponse(
                request.getId(),
                request.isRefundTarget(),
                request.isCompensationTarget(),
                request.getMessage(),
                request.getRequiredItems(),
                request.getStatus(),
                request.getDueAt(),
                request.getSentAt(),
                request.getCreatedAt());
    }
}
