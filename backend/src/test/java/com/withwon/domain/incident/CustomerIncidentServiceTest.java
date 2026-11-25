package com.withwon.domain.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentServiceTest {

    private IncidentRepository incidentRepository;
    private CustomerIncidentService customerIncidentService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        customerIncidentService = new CustomerIncidentService(incidentRepository);
    }

    @Test
    void findsOnlyIncidentsOwnedByCustomer() {
        List<Incident> incidents = List.of(mock(Incident.class));
        when(incidentRepository.findAllByCustomer_IdOrderByCreatedAtDesc(7L)).thenReturn(incidents);

        assertEquals(incidents, customerIncidentService.findCustomerIncidents(7L));
        verify(incidentRepository).findAllByCustomer_IdOrderByCreatedAtDesc(7L);
    }

    @Test
    void findsIncidentOnlyWhenItBelongsToCustomer() {
        Incident incident = mock(Incident.class);
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));

        assertEquals(Optional.of(incident), customerIncidentService.findCustomerIncident(7L, 12L));
        verify(incidentRepository).findByIdAndCustomer_Id(12L, 7L);
    }

    @Test
    void rejectsMissingCustomerId() {
        assertThrows(IllegalArgumentException.class,
                () -> customerIncidentService.findCustomerIncidents(null));
    }

    @Test
    void appliesStatusAndPaginationForCustomerIncidentList() {
        PageRequest pageable = PageRequest.of(1, 20);
        when(incidentRepository.findAllByCustomer_IdAndStatus(7L, "SUBMITTED", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        customerIncidentService.findCustomerIncidents(7L, "SUBMITTED", pageable);

        verify(incidentRepository).findAllByCustomer_IdAndStatus(7L, "SUBMITTED", pageable);
    }
}
