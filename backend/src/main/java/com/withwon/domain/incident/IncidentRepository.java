package com.withwon.domain.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Optional<Incident> findByIdAndCustomer_Id(Long id, Long customerId);
}
