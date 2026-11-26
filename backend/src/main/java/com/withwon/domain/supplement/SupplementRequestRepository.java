package com.withwon.domain.supplement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplementRequestRepository extends JpaRepository<SupplementRequest, Long> {

    List<SupplementRequest> findAllByIncident_IdOrderByCreatedAtDesc(Long incidentId);
}
