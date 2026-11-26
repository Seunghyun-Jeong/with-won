package com.withwon.customer.application.controller;

import com.withwon.customer.application.config.CustomerApplicationProperties;
import com.withwon.domain.checkitem.CheckItemConfigProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerApplicationConfigResponseTest {

    @Test
    void mapsConsentFormsAndCheckItemsToConfigurationResponse() {
        CustomerApplicationProperties properties = new CustomerApplicationProperties();
        properties.setSchemaVersion("v1");
        var form = new CustomerApplicationProperties.ConsentForm();
        form.setConsentType("PRIVACY");
        form.setConsentVersion("v1");
        form.setContent("privacy");
        properties.setConsentForms(List.of(form));

        CheckItemConfigProjection item = mock(CheckItemConfigProjection.class);
        when(item.getItemCode()).thenReturn("CHECK-01");
        when(item.getQuestion()).thenReturn("question");
        when(item.getAnswerType()).thenReturn("BOOLEAN");
        when(item.getAnswerOptions()).thenReturn("[]");
        when(item.getRequired()).thenReturn(true);
        when(item.getScope()).thenReturn("CUSTOMER");

        var response = CustomerApplicationConfigResponse.from(properties, List.of(item));

        assertEquals("v1", response.schemaVersion());
        assertEquals("PRIVACY", response.consentForms().get(0).consentType());
        assertEquals("CHECK-01", response.checkItems().get(0).itemCode());
        assertEquals(true, response.checkItems().get(0).required());
    }
}
