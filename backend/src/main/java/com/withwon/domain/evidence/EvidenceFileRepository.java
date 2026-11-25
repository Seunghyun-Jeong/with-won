package com.withwon.domain.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {

    List<EvidenceFile> findAllByIncident_Customer_IdOrderByCreatedAtDesc(Long customerId);

    List<EvidenceFile> findAllByIncident_IdAndIncident_Customer_IdOrderByCreatedAtDesc(
            Long incidentId, Long customerId);
}
