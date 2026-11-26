package com.withwon.customer.incident.controller;

import com.withwon.customer.intake.controller.CreatedApplicationResponse;
import com.withwon.domain.application.Application;
import com.withwon.domain.incident.Incident;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateIncidentDraftResponse(
        Long incidentId,
        List<CreatedApplicationResponse> applications,
        int selectedTransactionCount,
        long totalSelectedAmount,
        LocalDateTime savedAt,
        Long version
) {

    public static UpdateIncidentDraftResponse from(Incident incident,
                                                   List<Application> applications,
                                                   LocalDateTime savedAt) {
        return new UpdateIncidentDraftResponse(
                incident.getId(),
                applications.stream().map(CreatedApplicationResponse::from).toList(),
                0,
                0,
                savedAt,
                incident.getVersion());
    }
}
