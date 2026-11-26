package com.withwon.customer.notification.service;

import com.withwon.domain.notification.Notification;
import com.withwon.domain.notification.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerNotificationService {

    private final NotificationRepository notificationRepository;

    public CustomerNotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> findCustomerNotifications(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        return notificationRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
