package com.withwon.customer.incident.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.incident.service.CustomerIncidentDraftService;
import com.withwon.customer.incident.service.CustomerIncidentDraftUpdateService;
import com.withwon.customer.incident.service.CustomerSupplementResponseService;
import com.withwon.domain.evidence.EvidenceQueryService;
import com.withwon.domain.incident.CustomerIncidentService;
import com.withwon.domain.supplement.SupplementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIncidentControllerTest {

    private CustomerSupplementResponseService supplementResponseService;
    private CustomerIncidentController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        supplementResponseService = mock(CustomerSupplementResponseService.class);
        controller = new CustomerIncidentController(
                mock(CustomerIncidentService.class),
                mock(EvidenceQueryService.class),
                mock(CustomerIncidentDraftService.class),
                mock(CustomerIncidentDraftUpdateService.class),
                supplementResponseService);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal())
                .thenReturn(new AuthSessionService.SessionPrincipal(7L, "CUSTOMER"));
    }

    @Test
    void submitsSupplementResponseWithCustomerPrincipalAndIdempotencyKey() {
        SupplementResponse response = mock(SupplementResponse.class);
        when(response.getId()).thenReturn(301L);
        when(response.getSupplementRequestId()).thenReturn(55L);
        when(response.getSubmittedAt()).thenReturn(LocalDateTime.parse("2026-11-26T15:30:00"));
        SupplementResponseRequest request = new SupplementResponseRequest(
                "자료를 제출합니다.", java.util.List.of(101L), java.util.List.of(201L));
        when(supplementResponseService.submit(7L, 10L, 55L, request, "key-1"))
                .thenReturn(response);

        var result = controller.submitSupplementResponse(authentication, 10L, 55L, "key-1", request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(301L, result.getBody().supplementResponseId());
        assertEquals(55L, result.getBody().supplementRequestId());
        assertEquals("SUBMITTED", result.getBody().status());
        verify(supplementResponseService).submit(7L, 10L, 55L, request, "key-1");
    }
}
