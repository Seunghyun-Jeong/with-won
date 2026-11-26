package com.withwon.customer.notification.service;

import com.withwon.domain.notification.Notification;
import com.withwon.domain.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerNotificationServiceTest {

    private NotificationRepository notificationRepository;
    private CustomerNotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        service = new CustomerNotificationService(notificationRepository);
    }

    @Test
    void findsNotificationsForCustomerInNewestOrder() {
        List<Notification> notifications = List.of(mock(Notification.class));
        when(notificationRepository.findAllByCustomerIdOrderByCreatedAtDesc(7L)).thenReturn(notifications);

        assertEquals(notifications, service.findCustomerNotifications(7L));
        verify(notificationRepository).findAllByCustomerIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void rejectsMissingCustomerId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.findCustomerNotifications(null));
    }
}
