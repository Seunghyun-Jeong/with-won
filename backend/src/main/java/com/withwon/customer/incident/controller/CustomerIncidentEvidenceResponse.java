package com.withwon.customer.incident.controller;

import com.withwon.domain.evidence.EvidenceFile;

public record CustomerIncidentEvidenceResponse(
        Long evidenceFileId,
        String documentType,
        boolean refundTarget,
        boolean compensationTarget,
        String status
) {

    public static CustomerIncidentEvidenceResponse from(EvidenceFile evidenceFile) {
        return new CustomerIncidentEvidenceResponse(
                evidenceFile.getId(),
                evidenceFile.getDocumentType(),
                evidenceFile.isRefundTarget(),
                evidenceFile.isCompensationTarget(),
                evidenceFile.getStatus());
    }
}
