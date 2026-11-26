package com.withwon.customer.evidence.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.evidence.service.CustomerEvidenceUploadService;
import com.withwon.customer.evidence.service.CustomerEvidenceAccessService;
import com.withwon.domain.evidence.EvidenceQueryService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/incidents/{incidentId}/evidences")
public class CustomerEvidenceController {

    private final CustomerEvidenceUploadService uploadService;
    private final CustomerEvidenceAccessService accessService;
    private final EvidenceQueryService queryService;

    public CustomerEvidenceController(CustomerEvidenceUploadService uploadService,
                                      CustomerEvidenceAccessService accessService,
                                      EvidenceQueryService queryService) {
        this.uploadService = uploadService;
        this.accessService = accessService;
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<CustomerEvidenceListResponse>> list(
            Authentication authentication,
            @PathVariable Long incidentId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var evidences = queryService.findIncidentEvidenceFiles(principal.userId(), incidentId)
                .stream()
                .map(evidenceFile -> CustomerEvidenceListResponse.from(
                        incidentId,
                        evidenceFile,
                        queryService.findEvidenceVersions(principal.userId(), evidenceFile.getId())
                                .stream()
                                .findFirst()
                                .orElse(null)))
                .toList();
        return ResponseEntity.ok(evidences);
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

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<EvidenceUploadResponse>> uploadBatch(
            Authentication authentication,
            @PathVariable Long incidentId,
            @RequestParam("documentType") String documentType,
            @RequestParam(name = "refundTarget", defaultValue = "false") boolean refundTarget,
            @RequestParam(name = "compensationTarget", defaultValue = "false") boolean compensationTarget,
            @RequestPart("files") List<MultipartFile> files) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var uploaded = uploadService.uploadBatch(principal.userId(), incidentId, documentType,
                refundTarget, compensationTarget, files);
        return ResponseEntity.status(201)
                .body(uploaded.stream()
                        .map(item -> EvidenceUploadResponse.from(item.evidenceFile(), item.evidenceVersion()))
                        .toList());
    }

    @GetMapping("/{evidenceFileId}/content")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Resource> content(
            Authentication authentication,
            @PathVariable Long incidentId,
            @PathVariable Long evidenceFileId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var downloaded = accessService.load(principal.userId(), incidentId, evidenceFileId);
        MediaType mediaType = MediaType.parseMediaType(downloaded.version().getMimeType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(downloaded.version().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloaded.version().getOriginalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(downloaded.resource());
    }

    @DeleteMapping("/{evidenceFileId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long incidentId,
            @PathVariable Long evidenceFileId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        accessService.delete(principal.userId(), incidentId, evidenceFileId);
        return ResponseEntity.noContent().build();
    }
}
