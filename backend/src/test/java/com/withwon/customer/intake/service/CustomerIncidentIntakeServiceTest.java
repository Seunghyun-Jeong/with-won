package com.withwon.customer.intake.service;

import com.withwon.customer.intake.controller.CreateIncidentRequest;
import com.withwon.customer.intake.controller.IdempotencyConflictException;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.customer.Customer;
import com.withwon.domain.customer.CustomerRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentIntakeServiceTest {

    private CustomerRepository customerRepository;
    private IncidentRepository incidentRepository;
    private ApplicationRepository applicationRepository;
    private CustomerIncidentIntakeService intakeService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        incidentRepository = mock(IncidentRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        intakeService = new CustomerIncidentIntakeService(
                customerRepository, incidentRepository, applicationRepository);
    }

    @Test
    void createsDraftIncidentAndApplicationsAtomically() {
        Customer customer = mock(Customer.class);
        Incident incident = mock(Incident.class);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(incidentRepository.findByCustomer_IdAndSubmitKey(7L, "request-1"))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(applicationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = intakeService.create(
                7L,
                new CreateIncidentRequest(List.of("REFUND", "COMPENSATION")),
                "request-1");

        assertEquals(incident, result.incident());
        assertEquals(2, result.applications().size());
        verify(incidentRepository).save(any(Incident.class));
        verify(applicationRepository).saveAll(any());
    }

    @Test
    void returnsExistingResultForSameIdempotentRequest() {
        Incident incident = mock(Incident.class);
        Application application = mock(Application.class);
        when(incident.getSubmitRequestHash()).thenReturn(requestHash(List.of("REFUND")));
        when(incident.getId()).thenReturn(10L);
        when(incidentRepository.findByCustomer_IdAndSubmitKey(7L, "request-1"))
                .thenReturn(Optional.of(incident));
        when(applicationRepository.findAllByIncident_IdOrderByIdAsc(10L))
                .thenReturn(List.of(application));

        var result = intakeService.create(
                7L,
                new CreateIncidentRequest(List.of("REFUND")),
                "request-1");

        assertEquals(incident, result.incident());
        assertEquals(List.of(application), result.applications());
        verify(incidentRepository, never()).save(any(Incident.class));
        verify(applicationRepository, never()).saveAll(any());
    }

    @Test
    void rejectsSameKeyWithDifferentRequest() {
        Incident incident = mock(Incident.class);
        when(incident.getSubmitRequestHash()).thenReturn(requestHash(List.of("REFUND")));
        when(incidentRepository.findByCustomer_IdAndSubmitKey(7L, "request-1"))
                .thenReturn(Optional.of(incident));

        assertThrows(IdempotencyConflictException.class, () -> intakeService.create(
                7L,
                new CreateIncidentRequest(List.of("COMPENSATION")),
                "request-1"));
    }

    private String requestHash(List<String> applicationTypes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", applicationTypes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
