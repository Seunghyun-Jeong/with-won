package com.withwon.customer.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IncidentVersionConflictException extends RuntimeException {

    public IncidentVersionConflictException() {
        super("Incident version is stale.");
    }
}
