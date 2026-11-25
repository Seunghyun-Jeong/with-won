package com.withwon.customer.evidence.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.evidence.service.CustomerEvidenceUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customer/incidents/{incidentId}/evidences")
public class CustomerEvidenceController {

    private final CustomerEvidenceUploadService uploadService;

    public CustomerEvidenceController(CustomerEvidenceUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<EvidenceUploadResponse> upload(
            Authentication authentication,
            @PathVariable Long incidentId,
            @RequestParam("documentType") String documentType,
            @RequestParam(name = "refundTarget", defaultValue = "false") boolean refundTarget,
            @RequestParam(name = "compensationTarget", defaultValue = "false") boolean compensationTarget,
            @RequestPart("file") MultipartFile file) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var uploaded = uploadService.upload(principal.userId(), incidentId, documentType,
                refundTarget, compensationTarget, file);
        return ResponseEntity.status(201)
                .body(EvidenceUploadResponse.from(uploaded.evidenceFile(), uploaded.evidenceVersion()));
    }
}
