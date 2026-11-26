package com.withwon.customer.evidence.controller;

import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceVersion;

public record EvidenceUploadResponse(
        Long evidenceFileId,
        Long evidenceVersionId,
        String documentType,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String status
) {

    public static EvidenceUploadResponse from(EvidenceFile evidenceFile, EvidenceVersion evidenceVersion) {
        return new EvidenceUploadResponse(
                evidenceFile.getId(),
                evidenceVersion.getId(),
                evidenceFile.getDocumentType(),
                evidenceVersion.getOriginalName(),
                evidenceVersion.getMimeType(),
                evidenceVersion.getSizeBytes(),
                evidenceFile.getStatus());
    }
}
