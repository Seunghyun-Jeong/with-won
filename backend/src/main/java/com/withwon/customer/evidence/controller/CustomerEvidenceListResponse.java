package com.withwon.customer.evidence.controller;

import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceVersion;

public record CustomerEvidenceListResponse(
        Long evidenceFileId,
        String documentType,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String downloadUrl,
        boolean refundTarget,
        boolean compensationTarget,
        String status
) {

    public static CustomerEvidenceListResponse from(Long incidentId,
                                                    EvidenceFile evidenceFile,
                                                    EvidenceVersion latestVersion) {
        String downloadUrl = "/api/v1/customer/incidents/" + incidentId
                + "/evidences/" + evidenceFile.getId() + "/content";
        return new CustomerEvidenceListResponse(
                evidenceFile.getId(),
                evidenceFile.getDocumentType(),
                latestVersion == null ? null : latestVersion.getOriginalName(),
                latestVersion == null ? null : latestVersion.getMimeType(),
                latestVersion == null ? null : latestVersion.getSizeBytes(),
                downloadUrl,
                evidenceFile.isRefundTarget(),
                evidenceFile.isCompensationTarget(),
                evidenceFile.getStatus());
    }
}
