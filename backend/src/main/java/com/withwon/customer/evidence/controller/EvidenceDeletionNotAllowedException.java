package com.withwon.customer.evidence.controller;

public class EvidenceDeletionNotAllowedException extends RuntimeException {

    public EvidenceDeletionNotAllowedException() {
        super("Evidence can only be deleted while the incident is a draft.");
    }
}
