package com.withwon.customer.consent.controller;

import java.util.List;

public record ConsentSubmissionRequest(List<ConsentItem> consents) {
    public record ConsentItem(String consentType, String consentVersion, boolean agreed) {
    }
}
