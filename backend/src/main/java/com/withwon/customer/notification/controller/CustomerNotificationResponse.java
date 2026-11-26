package com.withwon.customer.notification.controller;

import com.withwon.domain.notification.Notification;

import java.time.LocalDateTime;

public record CustomerNotificationResponse(
        Long notificationId,
        Long incidentId,
        Long applicationId,
        String notificationType,
        String title,
        String message,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    public static CustomerNotificationResponse from(Notification notification) {
        return new CustomerNotificationResponse(
                notification.getId(),
                notification.getIncidentId(),
                notification.getApplicationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
