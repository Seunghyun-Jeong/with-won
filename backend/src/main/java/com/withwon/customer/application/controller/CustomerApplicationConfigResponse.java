package com.withwon.customer.application.controller;

import com.withwon.customer.application.config.CustomerApplicationProperties;
import com.withwon.domain.checkitem.CheckItemConfigProjection;

import java.util.List;

public record CustomerApplicationConfigResponse(
        String schemaVersion,
        List<ConsentFormResponse> consentForms,
        List<CheckItemResponse> checkItems) {

    public static CustomerApplicationConfigResponse from(CustomerApplicationProperties properties,
                                                         List<CheckItemConfigProjection> checkItems) {
        return new CustomerApplicationConfigResponse(
                properties.getSchemaVersion(),
                properties.getConsentForms().stream()
                        .map(form -> new ConsentFormResponse(
                                form.getConsentType(), form.getConsentVersion(), form.getContent()))
                        .toList(),
                checkItems.stream()
                        .map(item -> new CheckItemResponse(
                                item.getItemCode(), item.getQuestion(), item.getAnswerType(),
                                item.getAnswerOptions(), Boolean.TRUE.equals(item.getRequired()), item.getScope()))
                        .toList());
    }

    public record ConsentFormResponse(String consentType, String consentVersion, String content) {
    }

    public record CheckItemResponse(String itemCode, String question, String answerType,
                                    String answerOptions, boolean required, String scope) {
    }
}
