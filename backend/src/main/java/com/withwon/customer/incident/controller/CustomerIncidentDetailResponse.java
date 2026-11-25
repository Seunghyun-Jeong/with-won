package com.withwon.customer.incident.controller;

import com.withwon.domain.incident.Incident;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerIncidentDetailResponse(
        Long incidentId,
        String incidentNumber,
        CustomerProfileResponse customerProfile,
        String incidentStatus,
        String incidentDescription,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime closedAt,
        List<CustomerIncidentEvidenceResponse> evidenceFiles
) {

    public static CustomerIncidentDetailResponse from(Incident incident,
                                                      List<CustomerIncidentEvidenceResponse> evidenceFiles) {
        return new CustomerIncidentDetailResponse(
                incident.getId(),
                incident.getIncidentNumber(),
                CustomerProfileResponse.from(incident.getCustomer()),
                incident.getStatus(),
                incident.getDescription(),
                incident.getVersion(),
                incident.getCreatedAt(),
                incident.getSubmittedAt(),
                incident.getClosedAt(),
                evidenceFiles);
    }
}
