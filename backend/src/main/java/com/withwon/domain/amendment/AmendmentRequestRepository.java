package com.withwon.domain.amendment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmendmentRequestRepository extends JpaRepository<AmendmentRequest, Long> {

    List<AmendmentRequest> findAllByIncident_IdOrderByCreatedAtDesc(Long incidentId);
}
