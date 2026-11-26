package com.withwon.domain.notification;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Notification was not found.");
    }
}
