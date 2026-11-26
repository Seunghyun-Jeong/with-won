package com.withwon.customer.incident.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.domain.incident.CustomerIncidentService;
import com.withwon.domain.evidence.EvidenceQueryService;
import com.withwon.customer.incident.service.CustomerIncidentDraftService;
import com.withwon.customer.incident.service.CustomerIncidentDraftUpdateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/incidents")
public class CustomerIncidentController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerIncidentService customerIncidentService;
    private final EvidenceQueryService evidenceQueryService;
    private final CustomerIncidentDraftService draftService;
    private final CustomerIncidentDraftUpdateService draftUpdateService;

    public CustomerIncidentController(CustomerIncidentService customerIncidentService,
                                      EvidenceQueryService evidenceQueryService,
                                      CustomerIncidentDraftService draftService,
                                      CustomerIncidentDraftUpdateService draftUpdateService) {
        this.customerIncidentService = customerIncidentService;
        this.evidenceQueryService = evidenceQueryService;
        this.draftService = draftService;
        this.draftUpdateService = draftUpdateService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerIncidentListResponse> list(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100.");
        }

        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var incidents = customerIncidentService.findCustomerIncidents(principal.userId(), status, pageable);
        return ResponseEntity.ok(CustomerIncidentListResponse.from(incidents));
    }

    @GetMapping("/{incidentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerIncidentDetailResponse> detail(
            Authentication authentication,
            @PathVariable Long incidentId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var incident = customerIncidentService.findCustomerIncident(principal.userId(), incidentId)
                .orElseThrow(IncidentNotFoundException::new);
        var applications = customerIncidentService
                .findCustomerApplications(principal.userId(), incidentId)
                .stream()
                .map(com.withwon.customer.intake.controller.CreatedApplicationResponse::from)
                .toList();
        var consents = customerIncidentService.findCustomerConsents(principal.userId(), incidentId)
                .stream()
                .map(CustomerIncidentConsentResponse::from)
                .toList();
        var supplementRequests = customerIncidentService
                .findCustomerSupplementRequests(principal.userId(), incidentId)
                .stream()
                .map(CustomerIncidentSupplementResponse::from)
                .toList();
        var evidenceFiles = evidenceQueryService
                .findIncidentEvidenceFiles(principal.userId(), incidentId)
                .stream()
                .map(CustomerIncidentEvidenceResponse::from)
                .toList();
        return ResponseEntity.ok(CustomerIncidentDetailResponse.from(
                incident, applications, consents, supplementRequests, evidenceFiles));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{incidentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> deleteDraft(
            Authentication authentication,
            @PathVariable Long incidentId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        draftService.deleteDraft(principal.userId(), incidentId);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{incidentId}/draft")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<UpdateIncidentDraftResponse> updateDraft(
            Authentication authentication,
            @PathVariable Long incidentId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateIncidentDraftRequest request) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var updated = draftUpdateService.update(principal.userId(), incidentId, request);
        return ResponseEntity.ok(UpdateIncidentDraftResponse.from(
                updated.incident(), updated.applications(), updated.savedAt()));
    }
}
