package com.withwon.customer.intake.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.intake.service.CustomerIncidentIntakeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/incidents")
public class CustomerIntakeController {

    private final CustomerIncidentIntakeService intakeService;

    public CustomerIntakeController(CustomerIncidentIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CreateIncidentResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateIncidentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var created = intakeService.create(principal.userId(), request, idempotencyKey);
        return ResponseEntity.status(201)
                .body(CreateIncidentResponse.from(created.incident(), created.applications()));
    }
}
