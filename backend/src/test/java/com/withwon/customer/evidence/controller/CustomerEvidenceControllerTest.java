package com.withwon.customer.evidence.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.evidence.service.CustomerEvidenceAccessService;
import com.withwon.customer.evidence.service.CustomerEvidenceUploadService;
import com.withwon.domain.evidence.EvidenceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

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

    @Test
    void previewsBrowserSupportedEvidenceWhenRequested() {
        var version = mock(com.withwon.domain.evidence.EvidenceVersion.class);
        when(version.getMimeType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);
        when(version.getSizeBytes()).thenReturn(4L);
        when(version.getOriginalName()).thenReturn("receipt.pdf");
        when(accessService.load(7L, 10L, 55L))
                .thenReturn(new CustomerEvidenceAccessService.DownloadedEvidence(
                        null, version, new ByteArrayResource(new byte[]{1, 2, 3, 4})));

        var result = controller.content(authentication, 10L, 55L, true);

        assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
        String disposition = result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("inline"));
        assertTrue(disposition.contains("receipt.pdf"));
        assertEquals("nosniff", result.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void downloadsEvidenceByDefaultEvenWhenPreviewIsNotRequested() {
        var version = mock(com.withwon.domain.evidence.EvidenceVersion.class);
        when(version.getMimeType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);
        when(version.getSizeBytes()).thenReturn(4L);
        when(version.getOriginalName()).thenReturn("receipt.pdf");
        when(accessService.load(7L, 10L, 55L))
                .thenReturn(new CustomerEvidenceAccessService.DownloadedEvidence(
                        null, version, new ByteArrayResource(new byte[]{1, 2, 3, 4})));

        var result = controller.content(authentication, 10L, 55L, false);

        String disposition = result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment"));
        assertTrue(disposition.contains("receipt.pdf"));
    }
}
