package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.DraftDeletionNotAllowedException;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentDraftServiceTest {

    private IncidentRepository incidentRepository;
    private ApplicationRepository applicationRepository;
    private CustomerIncidentDraftService draftService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        draftService = new CustomerIncidentDraftService(incidentRepository, applicationRepository);
    }

    @Test
    void deletesApplicationsBeforeDraftIncident() {
        Incident incident = mock(Incident.class);
        when(incident.getStatus()).thenReturn("DRAFT");
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));

        draftService.deleteDraft(7L, 11L);

        verify(applicationRepository).deleteAllByIncident_Id(11L);
        verify(incidentRepository).delete(incident);
    }

    @Test
    void rejectsSubmittedIncidentDeletion() {
        Incident incident = mock(Incident.class);
        when(incident.getStatus()).thenReturn("SUBMITTED");
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));

        assertThrows(DraftDeletionNotAllowedException.class,
                () -> draftService.deleteDraft(7L, 11L));
        verify(applicationRepository, never()).deleteAllByIncident_Id(11L);
        verify(incidentRepository, never()).delete(incident);
    }

    @Test
    void rejectsIncidentOwnedByAnotherCustomer() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class,
                () -> draftService.deleteDraft(7L, 11L));
    }
}
