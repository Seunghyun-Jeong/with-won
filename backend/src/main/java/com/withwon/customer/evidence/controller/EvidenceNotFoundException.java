package com.withwon.customer.evidence.controller;

public class EvidenceNotFoundException extends RuntimeException {

    public EvidenceNotFoundException() {
        super("Evidence file was not found.");
    }
}
