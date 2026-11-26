package com.withwon.customer.application.controller;

import com.withwon.customer.application.config.CustomerApplicationProperties;

import java.util.List;

public record CustomerApplicationConfigResponse(
        String schemaVersion,
        List<ConsentFormResponse> consentForms) {

    public static CustomerApplicationConfigResponse from(CustomerApplicationProperties properties) {
        return new CustomerApplicationConfigResponse(
                properties.getSchemaVersion(),
                properties.getConsentForms().stream()
                        .map(form -> new ConsentFormResponse(
                                form.getConsentType(), form.getConsentVersion(), form.getContent()))
                        .toList());
    }

    public record ConsentFormResponse(String consentType, String consentVersion, String content) {
    }
}
