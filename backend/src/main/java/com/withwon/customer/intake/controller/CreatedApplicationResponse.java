package com.withwon.customer.intake.controller;

import com.withwon.domain.application.Application;

public record CreatedApplicationResponse(
        Long applicationId,
        String applicationType,
        String status,
        Long version
) {

    public static CreatedApplicationResponse from(Application application) {
        return new CreatedApplicationResponse(
                application.getId(),
                application.getApplicationType(),
                application.getStatus(),
                application.getVersion());
    }
}
