package com.withwon.customer.notification.service;

import com.withwon.domain.notification.Notification;
import com.withwon.domain.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void findsPagedNotificationsForCustomer() {
        PageRequest pageable = PageRequest.of(1, 20);
        when(notificationRepository.findAllByCustomerIdOrderByCreatedAtDescIdDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 21));

        service.findCustomerNotifications(7L, pageable);

        verify(notificationRepository).findAllByCustomerIdOrderByCreatedAtDescIdDesc(7L, pageable);
    }

    @Test
    void marksOnlyCustomerOwnedNotificationAsRead() {
        Notification notification = mock(Notification.class);
        when(notification.getReadAt()).thenReturn(null);
        when(notificationRepository.findByIdAndCustomerId(3L, 7L))
                .thenReturn(java.util.Optional.of(notification));

        service.markRead(7L, 3L);

        verify(notification).markRead(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marksAllUnreadCustomerNotificationsAsRead() {
        Notification first = mock(Notification.class);
        Notification second = mock(Notification.class);
        when(notificationRepository.findAllByCustomerIdAndReadAtIsNull(7L))
                .thenReturn(List.of(first, second));

        service.markAllRead(7L);

        verify(notificationRepository).findAllByCustomerIdAndReadAtIsNull(7L);
        verify(first).markRead(org.mockito.ArgumentMatchers.any());
        verify(second).markRead(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingCustomerIdWhenMarkingAllRead() {
        assertThrows(IllegalArgumentException.class,
                () -> service.markAllRead(null));
    }
}
