package com.withwon.customer.evidence.service;

import com.withwon.customer.incident.controller.DraftUpdateNotAllowedException;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceFileRepository;
import com.withwon.domain.evidence.EvidenceVersion;
import com.withwon.domain.evidence.EvidenceVersionRepository;
import com.withwon.domain.application.ApplicationConsentRepository;
import com.withwon.domain.incident.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerEvidenceUploadService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/gif",
            "image/png",
            "image/webp",
            "image/bmp",
            "image/tiff",
            "audio/mpeg",
            "audio/mp4",
            "audio/wav",
            "audio/x-wav",
            "audio/ogg",
            "audio/webm",
            "text/plain",
            "text/csv",
            "application/rtf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/x-hwp",
            "application/haansofthwp",
            "application/octet-stream");

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "com", "bat", "cmd", "ps1", "sh", "msi", "scr", "jar", "class", "js", "vbs");

    private final IncidentRepository incidentRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceVersionRepository evidenceVersionRepository;
    private final EvidenceStorage evidenceStorage;
    private final ApplicationConsentRepository consentRepository;

    public CustomerEvidenceUploadService(IncidentRepository incidentRepository,
                                         EvidenceFileRepository evidenceFileRepository,
                                         EvidenceVersionRepository evidenceVersionRepository,
                                         EvidenceStorage evidenceStorage) {
        this(incidentRepository, evidenceFileRepository, evidenceVersionRepository, evidenceStorage, null);
    }

    @Autowired
    public CustomerEvidenceUploadService(IncidentRepository incidentRepository,
                                         EvidenceFileRepository evidenceFileRepository,
                                         EvidenceVersionRepository evidenceVersionRepository,
                                         EvidenceStorage evidenceStorage,
                                         ApplicationConsentRepository consentRepository) {
        this.incidentRepository = incidentRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceVersionRepository = evidenceVersionRepository;
        this.evidenceStorage = evidenceStorage;
        this.consentRepository = consentRepository;
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
        if (consentRepository != null
                && (!consentRepository.existsByIncident_IdAndConsentType(incidentId, "PRIVACY")
                || !consentRepository.existsByIncident_IdAndConsentType(incidentId, "EXTERNAL_AI"))) {
            throw new IllegalStateException("Both PRIVACY and EXTERNAL_AI consents are required.");
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

    @Transactional
    public List<UploadedEvidence> uploadBatch(Long customerId, Long incidentId, String documentType,
                                              boolean refundTarget, boolean compensationTarget,
                                              List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty.");
        }

        return files.stream()
                .map(file -> upload(customerId, incidentId, documentType,
                        refundTarget, compensationTarget, file))
                .toList();
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
        String originalName = file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Executable files are not supported.");
        }
        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("This file type is not supported.");
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record UploadedEvidence(EvidenceFile evidenceFile, EvidenceVersion evidenceVersion) {
    }
}
