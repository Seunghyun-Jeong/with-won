package com.withwon.customer.evidence.service;

import com.withwon.customer.evidence.controller.EvidenceDeletionNotAllowedException;
import com.withwon.customer.evidence.controller.EvidenceNotFoundException;
import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceFileRepository;
import com.withwon.domain.evidence.EvidenceVersion;
import com.withwon.domain.evidence.EvidenceVersionRepository;
import com.withwon.domain.incident.IncidentRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerEvidenceAccessService {

    private final IncidentRepository incidentRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceVersionRepository evidenceVersionRepository;
    private final EvidenceStorage evidenceStorage;

    public CustomerEvidenceAccessService(IncidentRepository incidentRepository,
                                         EvidenceFileRepository evidenceFileRepository,
                                         EvidenceVersionRepository evidenceVersionRepository,
                                         EvidenceStorage evidenceStorage) {
        this.incidentRepository = incidentRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceVersionRepository = evidenceVersionRepository;
        this.evidenceStorage = evidenceStorage;
    }

    @Transactional(readOnly = true)
    public DownloadedEvidence load(Long customerId, Long incidentId, Long evidenceFileId) {
        EvidenceFile evidenceFile = findEvidence(customerId, incidentId, evidenceFileId);
        EvidenceVersion version = evidenceVersionRepository
                .findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(
                        evidenceFileId, customerId)
                .stream()
                .findFirst()
                .orElseThrow(EvidenceNotFoundException::new);
        Resource resource = evidenceStorage.load(version.getStorageKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw new EvidenceNotFoundException();
        }
        return new DownloadedEvidence(evidenceFile, version, resource);
    }

    @Transactional
    public void delete(Long customerId, Long incidentId, Long evidenceFileId) {
        EvidenceFile evidenceFile = findEvidence(customerId, incidentId, evidenceFileId);
        var incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(EvidenceNotFoundException::new);
        if (!incident.isDraft()) {
            throw new EvidenceDeletionNotAllowedException();
        }
        List<EvidenceVersion> versions = evidenceVersionRepository
                .findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(
                        evidenceFileId, customerId);
        evidenceVersionRepository.deleteAllByEvidenceFile_Id(evidenceFileId);
        evidenceFileRepository.delete(evidenceFile);
        versions.forEach(version -> evidenceStorage.delete(version.getStorageKey()));
    }

    private EvidenceFile findEvidence(Long customerId, Long incidentId, Long evidenceFileId) {
        return evidenceFileRepository
                .findByIdAndIncident_IdAndIncident_Customer_Id(evidenceFileId, incidentId, customerId)
                .orElseThrow(EvidenceNotFoundException::new);
    }

    public record DownloadedEvidence(EvidenceFile evidenceFile,
                                     EvidenceVersion version,
                                     Resource resource) {
    }
}
