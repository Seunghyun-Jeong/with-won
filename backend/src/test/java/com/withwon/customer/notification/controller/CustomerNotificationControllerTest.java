package com.withwon.customer.notification.controller;

import com.withwon.auth.AuthSessionService;
import com.withwon.customer.notification.service.CustomerNotificationService;
import com.withwon.domain.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerNotificationControllerTest {

    private CustomerNotificationService service;
    private CustomerNotificationController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = mock(CustomerNotificationService.class);
        controller = new CustomerNotificationController(service);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal())
                .thenReturn(new AuthSessionService.SessionPrincipal(7L, "CUSTOMER"));
    }

    @Test
    void returnsPagedNotifications() {
        when(service.findCustomerNotifications(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.<Notification>of(), PageRequest.of(1, 20), 21));

        var result = controller.page(authentication, 1, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().page());
        assertEquals(20, result.getBody().size());
    }

    @Test
    void rejectsInvalidPageSize() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.page(authentication, 0, 101));
    }

    @Test
    void marksAllNotificationsReadForAuthenticatedCustomer() {
        var result = controller.markAllRead(authentication);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(service).markAllRead(7L);
    }
}
