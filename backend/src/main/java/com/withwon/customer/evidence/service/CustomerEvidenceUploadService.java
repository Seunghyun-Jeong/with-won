package com.withwon.customer.evidence.service;

import com.withwon.customer.incident.controller.DraftUpdateNotAllowedException;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceFileRepository;
import com.withwon.domain.evidence.EvidenceVersion;
import com.withwon.domain.evidence.EvidenceVersionRepository;
import com.withwon.domain.incident.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerEvidenceUploadService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "audio/mpeg",
            "audio/mp4",
            "text/plain");

    private final IncidentRepository incidentRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceVersionRepository evidenceVersionRepository;
    private final EvidenceStorage evidenceStorage;

    public CustomerEvidenceUploadService(IncidentRepository incidentRepository,
                                         EvidenceFileRepository evidenceFileRepository,
                                         EvidenceVersionRepository evidenceVersionRepository,
                                         EvidenceStorage evidenceStorage) {
        this.incidentRepository = incidentRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceVersionRepository = evidenceVersionRepository;
        this.evidenceStorage = evidenceStorage;
    }

    @Transactional
    public UploadedEvidence upload(Long customerId, Long incidentId, String documentType,
                                   boolean refundTarget, boolean compensationTarget,
                                   MultipartFile file) {
        var incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (!incident.isDraft()) {
            throw new DraftUpdateNotAllowedException();
        }
        validate(documentType, file);

        var stored = evidenceStorage.store(file);
        var evidenceFile = evidenceFileRepository.save(new EvidenceFile(
                incident, documentType.trim().toUpperCase(Locale.ROOT),
                refundTarget, compensationTarget, "UPLOADED", "CUSTOMER"));
        var evidenceVersion = evidenceVersionRepository.save(new EvidenceVersion(
                evidenceFile, 1, stored.storageKey(), file.getOriginalFilename(),
                file.getContentType(), stored.sizeBytes(), stored.sha256(),
                "UPLOADED", "PENDING"));
        return new UploadedEvidence(evidenceFile, evidenceVersion);
    }

    private void validate(String documentType, MultipartFile file) {
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("documentType must not be blank.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("file must not exceed 20 MB.");
        }
        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only PDF, image, audio, and text files are supported.");
        }
    }

    public record UploadedEvidence(EvidenceFile evidenceFile, EvidenceVersion evidenceVersion) {
    }
}
