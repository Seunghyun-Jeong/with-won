package com.withwon.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    java.util.Optional<Notification> findByIdAndCustomerId(Long id, Long customerId);
}
