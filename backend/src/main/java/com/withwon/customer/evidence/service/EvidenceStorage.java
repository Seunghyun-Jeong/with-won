package com.withwon.customer.evidence.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

public interface EvidenceStorage {

    StoredEvidence store(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    record StoredEvidence(String storageKey, String sha256, long sizeBytes) {
    }
}
