package com.withwon.customer.notification.controller;

import org.springframework.data.domain.Page;

import java.util.List;

public record CustomerNotificationPageResponse(
        List<CustomerNotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static CustomerNotificationPageResponse from(Page<CustomerNotificationResponse> page) {
        return new CustomerNotificationPageResponse(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
