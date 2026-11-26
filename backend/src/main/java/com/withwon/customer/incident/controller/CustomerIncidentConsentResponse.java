package com.withwon.customer.incident.controller;

import com.withwon.domain.application.ApplicationConsent;

import java.time.LocalDateTime;

public record CustomerIncidentConsentResponse(
        Long consentId,
        Long applicationId,
        Long formVersionId,
        String consentType,
        String policyVersion,
        LocalDateTime agreedAt,
        String policyText
) {

    public static CustomerIncidentConsentResponse from(ApplicationConsent consent) {
        return new CustomerIncidentConsentResponse(
                consent.getId(),
                consent.getApplication() == null ? null : consent.getApplication().getId(),
                consent.getFormVersion() == null ? null : consent.getFormVersion().getId(),
                consent.getConsentType(),
                consent.getPolicyVersion(),
                consent.getAgreedAt(),
                consent.getPolicyText());
    }
}
