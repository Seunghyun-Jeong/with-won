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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/notifications")
public class CustomerNotificationController {

    private static final int MAX_PAGE_SIZE = 100;

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

    @GetMapping("/page")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerNotificationPageResponse> page(
            Authentication authentication,
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
        var notifications = notificationService.findCustomerNotifications(
                        principal.userId(), PageRequest.of(page, size,
                                Sort.by(Sort.Direction.DESC, "createdAt").and(
                                        Sort.by(Sort.Direction.DESC, "id"))))
                .map(CustomerNotificationResponse::from);
        return ResponseEntity.ok(CustomerNotificationPageResponse.from(notifications));
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

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        notificationService.markAllRead(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
