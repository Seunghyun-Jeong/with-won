package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.IncidentVersionConflictException;
import com.withwon.customer.incident.controller.UpdateIncidentDraftRequest;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentDraftUpdateServiceTest {

    private IncidentRepository incidentRepository;
    private ApplicationRepository applicationRepository;
    private CustomerIncidentDraftUpdateService updateService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        updateService = new CustomerIncidentDraftUpdateService(
                incidentRepository,
                applicationRepository,
                Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void updatesDescriptionWithoutReplacingApplicationsWhenFieldIsAbsent() {
        Incident incident = draftIncident(4L);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));
        when(applicationRepository.findAllByIncident_IdOrderByIdAsc(4L)).thenReturn(List.of());

        updateService.update(7L, 11L, new UpdateIncidentDraftRequest(null, "updated", 4L));

        verify(applicationRepository, never()).deleteAllByIncident_Id(11L);
        verify(applicationRepository).findAllByIncident_IdOrderByIdAsc(11L);
        verify(incident).updateDescription("updated");
    }

    @Test
    void replacesDraftApplicationsWhenApplicationTypesAreProvided() {
        Incident incident = draftIncident(2L);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));
        when(applicationRepository.findAllByIncident_IdOrderByIdAsc(11L)).thenReturn(List.of());
        when(applicationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        updateService.update(7L, 11L,
                new UpdateIncidentDraftRequest(List.of("COMPENSATION"), null, 2L));

        verify(applicationRepository).deleteAllByIncident_Id(11L);
        verify(applicationRepository).saveAll(any());
    }

    @Test
    void rejectsStaleVersion() {
        Incident incident = draftIncident(3L);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));

        assertThrows(IncidentVersionConflictException.class,
                () -> updateService.update(7L, 11L,
                        new UpdateIncidentDraftRequest(null, "stale", 2L)));
        verify(incident, never()).updateDescription("stale");
    }

    private Incident draftIncident(Long version) {
        Incident incident = mock(Incident.class);
        when(incident.isDraft()).thenReturn(true);
        when(incident.getVersion()).thenReturn(version);
        when(incident.getId()).thenReturn(11L);
        return incident;
    }
}
