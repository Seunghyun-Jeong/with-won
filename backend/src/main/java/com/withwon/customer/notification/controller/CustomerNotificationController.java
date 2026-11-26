package com.withwon.customer.notification.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.notification.service.CustomerNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/notifications")
public class CustomerNotificationController {

    private final CustomerNotificationService notificationService;

    public CustomerNotificationController(CustomerNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<CustomerNotificationResponse>> list(Authentication authentication) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        var notifications = notificationService.findCustomerNotifications(principal.userId())
                .stream()
                .map(CustomerNotificationResponse::from)
                .toList();
        return ResponseEntity.ok(notifications);
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> markRead(Authentication authentication,
                                         @PathVariable Long notificationId) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        notificationService.markRead(principal.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
