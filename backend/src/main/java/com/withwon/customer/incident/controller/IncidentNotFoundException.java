package com.withwon.customer.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException() {
        super("Incident not found.");
    }
}
