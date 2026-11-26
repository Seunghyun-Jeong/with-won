package com.withwon.customer.consent.controller;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentSubmissionResponse(
        List<ConsentItemResponse> consents,
        boolean evidenceUploadAvailable,
        LocalDateTime agreedAt,
        Long version) {

    public record ConsentItemResponse(String consentType, String consentVersion, boolean agreed) {
    }
}
