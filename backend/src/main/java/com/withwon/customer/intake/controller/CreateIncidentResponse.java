package com.withwon.customer.intake.controller;

import com.withwon.domain.application.Application;
import com.withwon.domain.incident.Incident;

import java.util.List;

public record CreateIncidentResponse(
        Long incidentId,
        String incidentStatus,
        List<CreatedApplicationResponse> applications,
        Long version
) {

    public static CreateIncidentResponse from(Incident incident, List<Application> applications) {
        return new CreateIncidentResponse(
                incident.getId(),
                incident.getStatus(),
                applications.stream().map(CreatedApplicationResponse::from).toList(),
                incident.getVersion());
    }
}
