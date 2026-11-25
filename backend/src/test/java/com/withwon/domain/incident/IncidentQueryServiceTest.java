package com.withwon.domain.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentQueryServiceTest {

    private IncidentRepository incidentRepository;
    private IncidentQueryService incidentQueryService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        incidentQueryService = new IncidentQueryService(incidentRepository);
    }

    @Test
    void findsOnlyIncidentsOwnedByCustomer() {
        List<Incident> incidents = List.of(mock(Incident.class));
        when(incidentRepository.findAllByCustomer_IdOrderByCreatedAtDesc(7L)).thenReturn(incidents);

        assertEquals(incidents, incidentQueryService.findCustomerIncidents(7L));
        verify(incidentRepository).findAllByCustomer_IdOrderByCreatedAtDesc(7L);
    }

    @Test
    void findsIncidentOnlyWhenItBelongsToCustomer() {
        Incident incident = mock(Incident.class);
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));

        assertEquals(Optional.of(incident), incidentQueryService.findCustomerIncident(7L, 12L));
        verify(incidentRepository).findByIdAndCustomer_Id(12L, 7L);
    }

    @Test
    void rejectsMissingCustomerId() {
        assertThrows(IllegalArgumentException.class,
                () -> incidentQueryService.findCustomerIncidents(null));
    }
}
