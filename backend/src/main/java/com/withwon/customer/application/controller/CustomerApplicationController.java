package com.withwon.customer.application.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.application.config.CustomerApplicationProperties;
import com.withwon.domain.checkitem.CheckItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/application-config")
public class CustomerApplicationController {

    private final CustomerApplicationProperties properties;
    private final CheckItemRepository checkItemRepository;

    public CustomerApplicationController(CustomerApplicationProperties properties,
                                         CheckItemRepository checkItemRepository) {
        this.properties = properties;
        this.checkItemRepository = checkItemRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerApplicationConfigResponse> config(
            Authentication authentication,
            @RequestParam(required = false) String schemaVersion) {
        if (schemaVersion != null && !schemaVersion.equals(properties.getSchemaVersion())) {
            throw new IllegalArgumentException("Unsupported schemaVersion.");
        }
        return ResponseEntity.ok(CustomerApplicationConfigResponse.from(
                properties, checkItemRepository.findEnabledConfigItems(properties.getSchemaVersion())));
    }
}
