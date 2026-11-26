package com.withwon.domain.supplement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplementRequestRepository extends JpaRepository<SupplementRequest, Long> {

    List<SupplementRequest> findAllByIncident_IdOrderByCreatedAtDesc(Long incidentId);

    java.util.Optional<SupplementRequest> findByIdAndIncident_IdAndIncident_Customer_Id(
            Long id, Long incidentId, Long customerId);
}
