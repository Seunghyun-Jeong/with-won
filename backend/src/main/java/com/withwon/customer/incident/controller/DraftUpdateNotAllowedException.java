package com.withwon.customer.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DraftUpdateNotAllowedException extends RuntimeException {

    public DraftUpdateNotAllowedException() {
        super("Only draft incidents can be updated.");
    }
}
