package com.withwon.customer.evidence.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.evidence.service.CustomerEvidenceAccessService;
import com.withwon.customer.evidence.service.CustomerEvidenceUploadService;
import com.withwon.domain.evidence.EvidenceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerEvidenceControllerTest {

    private CustomerEvidenceUploadService uploadService;
    private CustomerEvidenceAccessService accessService;
    private EvidenceQueryService queryService;
    private CustomerEvidenceController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        uploadService = mock(CustomerEvidenceUploadService.class);
        accessService = mock(CustomerEvidenceAccessService.class);
        queryService = mock(EvidenceQueryService.class);
        controller = new CustomerEvidenceController(uploadService, accessService, queryService);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal())
                .thenReturn(new AuthSessionService.SessionPrincipal(7L, "CUSTOMER"));
    }

    @Test
    void uploadsBatchWithoutImposingFileCountLimit() {
        List<MultipartFile> files = List.of(mock(MultipartFile.class), mock(MultipartFile.class));
        when(uploadService.uploadBatch(7L, 10L, "BANK_STATEMENT", true, false, files))
                .thenReturn(List.of());

        var result = controller.uploadBatch(authentication, 10L, "BANK_STATEMENT", true, false, files);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(List.of(), result.getBody());
        verify(uploadService).uploadBatch(7L, 10L, "BANK_STATEMENT", true, false, files);
    }

    @Test
    void deletesEvidenceForAuthenticatedCustomer() {
        var result = controller.delete(authentication, 10L, 55L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(accessService).delete(7L, 10L, 55L);
    }
}
