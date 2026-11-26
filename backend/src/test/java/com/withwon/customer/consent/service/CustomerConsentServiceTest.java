package com.withwon.customer.consent.service;

import com.withwon.customer.application.config.CustomerApplicationProperties;
import com.withwon.customer.consent.controller.ConsentSubmissionRequest;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.application.ApplicationConsentRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerConsentServiceTest {

    private IncidentRepository incidentRepository;
    private ApplicationConsentRepository consentRepository;
    private CustomerConsentService service;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        consentRepository = mock(ApplicationConsentRepository.class);
        CustomerApplicationProperties properties = new CustomerApplicationProperties();
        properties.setSchemaVersion("v1");
        properties.setConsentForms(List.of(
                form("PRIVACY", "v1", "privacy text"),
                form("EXTERNAL_AI", "v1", "ai text")));
        service = new CustomerConsentService(incidentRepository, consentRepository, properties);
    }

    @Test
    void savesBothConfiguredConsentsWithServerTimeAndPolicyText() {
        Incident incident = mock(Incident.class);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.of(incident));
        when(incident.getVersion()).thenReturn(3L);

        var response = service.submit(7L, 11L, new ConsentSubmissionRequest(List.of(
                new ConsentSubmissionRequest.ConsentItem("PRIVACY", "v1", true),
                new ConsentSubmissionRequest.ConsentItem("EXTERNAL_AI", "v1", true))));

        assertEquals(true, response.evidenceUploadAvailable());
        assertEquals(3L, response.version());
        verify(consentRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void rejectsSubmissionWhenOneConsentIsNotAgreed() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.of(mock(Incident.class)));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit(7L, 11L, new ConsentSubmissionRequest(List.of(
                        new ConsentSubmissionRequest.ConsentItem("PRIVACY", "v1", true),
                        new ConsentSubmissionRequest.ConsentItem("EXTERNAL_AI", "v1", false)))));
    }

    @Test
    void rejectsIncidentOwnedByAnotherCustomer() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class,
                () -> service.submit(7L, 11L, new ConsentSubmissionRequest(List.of())));
    }

    private CustomerApplicationProperties.ConsentForm form(String type, String version, String content) {
        CustomerApplicationProperties.ConsentForm form = new CustomerApplicationProperties.ConsentForm();
        form.setConsentType(type);
        form.setConsentVersion(version);
        form.setContent(content);
        return form;
    }
}
