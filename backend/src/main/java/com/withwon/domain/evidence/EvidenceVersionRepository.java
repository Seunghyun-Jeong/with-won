package com.withwon.domain.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, Long> {

    List<EvidenceVersion> findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(
            Long evidenceFileId, Long customerId);
}
