package com.withwon.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Notification> findAllByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    List<Notification> findAllByCustomerIdAndReadAtIsNull(Long customerId);

    java.util.Optional<Notification> findByIdAndCustomerId(Long id, Long customerId);
}
