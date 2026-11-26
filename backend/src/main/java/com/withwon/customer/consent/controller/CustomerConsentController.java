package com.withwon.customer.consent.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.consent.service.CustomerConsentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/incidents/{incidentId}/consents")
public class CustomerConsentController {

    private final CustomerConsentService consentService;

    public CustomerConsentController(CustomerConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ConsentSubmissionResponse> submit(
            Authentication authentication,
            @PathVariable Long incidentId,
            @RequestBody ConsentSubmissionRequest request) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        return ResponseEntity.status(201).body(
                consentService.submit(principal.userId(), incidentId, request));
    }
}
