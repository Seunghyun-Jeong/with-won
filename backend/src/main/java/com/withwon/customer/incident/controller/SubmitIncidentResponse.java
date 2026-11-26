package com.withwon.customer.incident.controller;

import java.time.LocalDateTime;
import java.util.List;

public record SubmitIncidentResponse(
        String incidentNumber,
        List<Long> applicationIds,
        LocalDateTime submittedAt,
        Long analysisJobId,
        Long version
) {
}
