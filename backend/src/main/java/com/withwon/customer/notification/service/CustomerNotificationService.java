package com.withwon.customer.notification.service;

import com.withwon.domain.notification.Notification;
import com.withwon.domain.notification.NotificationRepository;
import com.withwon.domain.notification.NotificationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

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

    @Transactional
    public void markRead(Long customerId, Long notificationId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null.");
        }
        Notification notification = notificationRepository.findByIdAndCustomerId(notificationId, customerId)
                .orElseThrow(NotificationNotFoundException::new);
        if (notification.getReadAt() == null) {
            notification.markRead(LocalDateTime.now());
        }
    }
}
