package com.withwon.domain.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_versions")
public class EvidenceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_file_id", nullable = false)
    private EvidenceFile evidenceFile;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "extraction_status", nullable = false, length = 24)
    private String extractionStatus;

    @Column(name = "extracted_chunks", columnDefinition = "json")
    private String extractedChunks;

    @Column(name = "masked_chunks", columnDefinition = "json")
    private String maskedChunks;

    @Column(name = "extraction_error", length = 255)
    private String extractionError;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected EvidenceVersion() {
    }

    public EvidenceVersion(EvidenceFile evidenceFile, Integer versionNo, String storageKey,
                           String originalName, String mimeType, Long sizeBytes, String sha256,
                           String status, String extractionStatus) {
        this.evidenceFile = evidenceFile;
        this.versionNo = versionNo;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = status;
        this.extractionStatus = extractionStatus;
    }

    public Long getId() {
        return id;
    }

    public EvidenceFile getEvidenceFile() {
        return evidenceFile;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStatus() {
        return status;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public String getExtractedChunks() {
        return extractedChunks;
    }

    public String getMaskedChunks() {
        return maskedChunks;
    }

    public String getExtractionError() {
        return extractionError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
