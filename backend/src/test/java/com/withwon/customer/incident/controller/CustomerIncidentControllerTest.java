package com.withwon.customer.incident.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.evidence.service.CustomerEvidenceAccessService;
import com.withwon.customer.evidence.service.CustomerEvidenceUploadService;
import com.withwon.customer.incident.service.CustomerIncidentDraftService;
import com.withwon.customer.incident.service.CustomerIncidentDraftUpdateService;
import com.withwon.customer.incident.service.CustomerSupplementResponseService;
import com.withwon.customer.transaction.service.CustomerTransactionService;
import com.withwon.domain.customer.Customer;
import com.withwon.domain.evidence.EvidenceQueryService;
import com.withwon.domain.incident.CustomerIncidentService;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.supplement.SupplementResponse;
import com.withwon.domain.transaction.TransactionSearchProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentControllerTest {

    private CustomerIncidentService incidentService;
    private EvidenceQueryService evidenceQueryService;
    private CustomerIncidentController controller;
    private CustomerTransactionService transactionService;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        incidentService = mock(CustomerIncidentService.class);
        evidenceQueryService = mock(EvidenceQueryService.class);
        transactionService = mock(CustomerTransactionService.class);
        controller = new CustomerIncidentController(
                incidentService,
                evidenceQueryService,
                mock(CustomerIncidentDraftService.class),
                mock(CustomerIncidentDraftUpdateService.class),
                mock(CustomerSupplementResponseService.class),
                transactionService);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal())
                .thenReturn(new AuthSessionService.SessionPrincipal(7L, "CUSTOMER"));
    }

    @Test
    void returnsCustomerIncidentPageWithValidatedParameters() {
        when(incidentService.findCustomerIncidents(7L, "SUBMITTED",
                PageRequest.of(1, 20, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 21));

        var result = controller.list(authentication, "SUBMITTED", 1, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().page());
        assertEquals(20, result.getBody().size());
    }

    @Test
    void rejectsIncidentListPageSizeOverMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.list(authentication, null, 0, 101));
    }

    @Test
    void returnsAvailableTransactionsForAuthenticatedCustomer() {
        when(transactionService.findAvailableTransactions(
                7L, 10L, null, null, 22L, 1, 20))
                .thenReturn(new PageImpl<TransactionSearchProjection>(
                        List.of(), PageRequest.of(1, 20), 0));

        var result = controller.availableTransactions(
                authentication, 10L, null, null, 22L, 1, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().page());
        verify(transactionService).findAvailableTransactions(
                7L, 10L, null, null, 22L, 1, 20);
    }

    @Test
    void returnsOnlyCustomerOwnedIncidentDetail() {
        Incident incident = mock(Incident.class);
        Customer customer = mock(Customer.class);
        when(incident.getId()).thenReturn(10L);
        when(incident.getCustomer()).thenReturn(customer);
        when(incidentService.findCustomerIncident(7L, 10L)).thenReturn(Optional.of(incident));
        when(incidentService.findCustomerApplications(7L, 10L)).thenReturn(List.of());
        when(incidentService.findCustomerConsents(7L, 10L)).thenReturn(List.of());
        when(incidentService.findCustomerSupplementRequests(7L, 10L)).thenReturn(List.of());
        when(incidentService.findCustomerAmendmentRequests(7L, 10L)).thenReturn(List.of());
        when(incidentService.findCustomerRefundResults(7L, 10L)).thenReturn(List.of());
        when(evidenceQueryService.findIncidentEvidenceFiles(7L, 10L)).thenReturn(List.of());

        var result = controller.detail(authentication, 10L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(incidentService).findCustomerIncident(7L, 10L);
    }
}
