package com.withwon.domain.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, Long> {

    List<EvidenceVersion> findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(
            Long evidenceFileId, Long customerId);

    long countByIdInAndEvidenceFile_Incident_IdAndEvidenceFile_Incident_Customer_Id(
            java.util.Collection<Long> ids, Long incidentId, Long customerId);

    void deleteAllByEvidenceFile_Id(Long evidenceFileId);
}
