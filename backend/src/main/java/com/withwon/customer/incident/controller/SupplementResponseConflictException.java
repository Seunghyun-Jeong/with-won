package com.withwon.customer.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class SupplementResponseConflictException extends RuntimeException {

    public SupplementResponseConflictException() {
        super("Idempotency-Key was already used with a different supplement response.");
    }
}
