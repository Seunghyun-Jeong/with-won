package com.withwon.domain.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Page<Incident> findAllByCustomer_Id(Long customerId, Pageable pageable);

    Page<Incident> findAllByCustomer_IdAndStatus(Long customerId, String status, Pageable pageable);

    Optional<Incident> findByIdAndCustomer_Id(Long id, Long customerId);
}
