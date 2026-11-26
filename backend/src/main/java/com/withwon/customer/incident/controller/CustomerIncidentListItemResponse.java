package com.withwon.customer.incident.controller;

import com.withwon.domain.incident.Incident;

import java.time.LocalDateTime;

public record CustomerIncidentListItemResponse(
        Long incidentId,
        String incidentNumber,
        String incidentStatus,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime submittedAt
) {

    public static CustomerIncidentListItemResponse from(Incident incident) {
        return new CustomerIncidentListItemResponse(
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getStatus(),
                incident.getVersion(),
                incident.getCreatedAt(),
                incident.getSubmittedAt());
    }
}
