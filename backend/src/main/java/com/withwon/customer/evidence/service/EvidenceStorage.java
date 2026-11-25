package com.withwon.customer.evidence.service;

import org.springframework.web.multipart.MultipartFile;

public interface EvidenceStorage {

    StoredEvidence store(MultipartFile file);

    record StoredEvidence(String storageKey, String sha256, long sizeBytes) {
    }
}
