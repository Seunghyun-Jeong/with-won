package com.withwon.customer.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DraftDeletionNotAllowedException extends RuntimeException {

    public DraftDeletionNotAllowedException() {
        super("Only draft incidents can be deleted.");
    }
}
