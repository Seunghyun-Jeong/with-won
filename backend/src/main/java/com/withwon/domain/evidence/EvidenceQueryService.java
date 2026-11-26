package com.withwon.domain.evidence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EvidenceQueryService {

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceVersionRepository evidenceVersionRepository;

    public EvidenceQueryService(EvidenceFileRepository evidenceFileRepository,
                                EvidenceVersionRepository evidenceVersionRepository) {
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceVersionRepository = evidenceVersionRepository;
    }

    public List<EvidenceFile> findCustomerEvidenceFiles(Long customerId) {
        requireId(customerId, "customerId");
        return evidenceFileRepository.findAllByIncident_Customer_IdOrderByCreatedAtDesc(customerId);
    }

    public List<EvidenceFile> findIncidentEvidenceFiles(Long customerId, Long incidentId) {
        requireId(customerId, "customerId");
        requireId(incidentId, "incidentId");
        return evidenceFileRepository
                .findAllByIncident_IdAndIncident_Customer_IdOrderByCreatedAtDesc(incidentId, customerId);
    }

    public List<EvidenceVersion> findEvidenceVersions(Long customerId, Long evidenceFileId) {
        requireId(customerId, "customerId");
        requireId(evidenceFileId, "evidenceFileId");
        return evidenceVersionRepository
                .findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(
                        evidenceFileId, customerId);
    }

    private void requireId(Long id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
    }
}
