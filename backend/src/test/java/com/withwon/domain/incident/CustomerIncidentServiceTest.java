package com.withwon.domain.incident;

import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.application.ApplicationConsentRepository;
import com.withwon.domain.supplement.SupplementRequestRepository;
import com.withwon.domain.amendment.AmendmentRequestRepository;
import com.withwon.domain.refund.RefundResultRecordRepository;
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
    private ApplicationRepository applicationRepository;
    private ApplicationConsentRepository applicationConsentRepository;
    private SupplementRequestRepository supplementRequestRepository;
    private AmendmentRequestRepository amendmentRequestRepository;
    private RefundResultRecordRepository refundResultRecordRepository;
    private CustomerIncidentService customerIncidentService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        applicationConsentRepository = mock(ApplicationConsentRepository.class);
        supplementRequestRepository = mock(SupplementRequestRepository.class);
        amendmentRequestRepository = mock(AmendmentRequestRepository.class);
        refundResultRecordRepository = mock(RefundResultRecordRepository.class);
        customerIncidentService = new CustomerIncidentService(
                incidentRepository, applicationRepository, applicationConsentRepository,
                supplementRequestRepository, amendmentRequestRepository, refundResultRecordRepository);
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
    void findsApplicationsOnlyForCustomerOwnedIncident() {
        Incident incident = mock(Incident.class);
        List<com.withwon.domain.application.Application> applications = List.of(
                mock(com.withwon.domain.application.Application.class));
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));
        when(applicationRepository.findAllByIncident_IdOrderByIdAsc(12L)).thenReturn(applications);

        assertEquals(applications, customerIncidentService.findCustomerApplications(7L, 12L));
        verify(applicationRepository).findAllByIncident_IdOrderByIdAsc(12L);
    }

    @Test
    void findsConsentsOnlyForCustomerOwnedIncident() {
        Incident incident = mock(Incident.class);
        List<com.withwon.domain.application.ApplicationConsent> consents = List.of(
                mock(com.withwon.domain.application.ApplicationConsent.class));
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));
        when(applicationConsentRepository.findAllByIncident_IdOrderByAgreedAtDesc(12L))
                .thenReturn(consents);

        assertEquals(consents, customerIncidentService.findCustomerConsents(7L, 12L));
        verify(applicationConsentRepository).findAllByIncident_IdOrderByAgreedAtDesc(12L);
    }

    @Test
    void findsSupplementRequestsOnlyForCustomerOwnedIncident() {
        Incident incident = mock(Incident.class);
        List<com.withwon.domain.supplement.SupplementRequest> requests = List.of(
                mock(com.withwon.domain.supplement.SupplementRequest.class));
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));
        when(supplementRequestRepository.findAllByIncident_IdOrderByCreatedAtDesc(12L))
                .thenReturn(requests);

        assertEquals(requests, customerIncidentService.findCustomerSupplementRequests(7L, 12L));
        verify(supplementRequestRepository).findAllByIncident_IdOrderByCreatedAtDesc(12L);
    }

    @Test
    void findsAmendmentRequestsOnlyForCustomerOwnedIncident() {
        Incident incident = mock(Incident.class);
        List<com.withwon.domain.amendment.AmendmentRequest> requests = List.of(
                mock(com.withwon.domain.amendment.AmendmentRequest.class));
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));
        when(amendmentRequestRepository.findAllByIncident_IdOrderByCreatedAtDesc(12L))
                .thenReturn(requests);

        assertEquals(requests, customerIncidentService.findCustomerAmendmentRequests(7L, 12L));
        verify(amendmentRequestRepository).findAllByIncident_IdOrderByCreatedAtDesc(12L);
    }

    @Test
    void findsRefundResultsOnlyForCustomerOwnedIncident() {
        Incident incident = mock(Incident.class);
        List<com.withwon.domain.refund.RefundResultRecord> results = List.of(
                mock(com.withwon.domain.refund.RefundResultRecord.class));
        when(incidentRepository.findByIdAndCustomer_Id(12L, 7L)).thenReturn(Optional.of(incident));
        when(refundResultRecordRepository.findAllByIncident_IdOrderByVersionNoDesc(12L))
                .thenReturn(results);

        assertEquals(results, customerIncidentService.findCustomerRefundResults(7L, 12L));
        verify(refundResultRecordRepository).findAllByIncident_IdOrderByVersionNoDesc(12L);
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
