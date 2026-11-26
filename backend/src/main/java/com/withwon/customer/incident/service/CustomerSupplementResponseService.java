package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.SupplementResponseConflictException;
import com.withwon.customer.incident.controller.SupplementResponseRequest;
import com.withwon.domain.evidence.EvidenceVersionRepository;
import com.withwon.domain.application.ApplicationFormVersionRepository;
import com.withwon.domain.supplement.SupplementRequestNotFoundException;
import com.withwon.domain.supplement.SupplementRequestRepository;
import com.withwon.domain.supplement.SupplementResponse;
import com.withwon.domain.supplement.SupplementResponseRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class CustomerSupplementResponseService {

    private final SupplementRequestRepository requestRepository;
    private final SupplementResponseRepository responseRepository;
    private final EvidenceVersionRepository evidenceVersionRepository;
    private final ApplicationFormVersionRepository formVersionRepository;

    public CustomerSupplementResponseService(SupplementRequestRepository requestRepository,
                                             SupplementResponseRepository responseRepository,
                                             EvidenceVersionRepository evidenceVersionRepository,
                                             ApplicationFormVersionRepository formVersionRepository) {
        this.requestRepository = requestRepository;
        this.responseRepository = responseRepository;
        this.evidenceVersionRepository = evidenceVersionRepository;
        this.formVersionRepository = formVersionRepository;
    }

    @Transactional
    public SupplementResponse submit(Long customerId, Long incidentId, Long supplementRequestId,
                                     SupplementResponseRequest request, String idempotencyKey) {
        requireIds(customerId, incidentId, supplementRequestId);
        if (request == null) {
            throw new IllegalArgumentException("request must not be null.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank.");
        }

        List<Long> evidenceIds = normalizedIds(request.evidenceVersionIds(), "evidenceVersionIds");
        List<Long> formIds = normalizedIds(request.formVersionIds(), "formVersionIds");
        String requestHash = hashRequest(request.message(), evidenceIds, formIds);
        var existing = responseRepository.findByRequestKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getRequestHash())) {
                throw new SupplementResponseConflictException();
            }
            return existing.get();
        }

        var supplementRequest = requestRepository
                .findByIdAndIncident_IdAndIncident_Customer_Id(supplementRequestId, incidentId, customerId)
                .orElseThrow(SupplementRequestNotFoundException::new);
        long evidenceCount = evidenceVersionRepository
                .countByIdInAndEvidenceFile_Incident_IdAndEvidenceFile_Incident_Customer_Id(
                        evidenceIds, incidentId, customerId);
        if (evidenceCount != evidenceIds.size()) {
            throw new IllegalArgumentException("evidenceVersionIds must belong to the customer incident.");
        }
        long formCount = formVersionRepository
                .countByIdInAndApplication_Incident_IdAndApplication_Incident_Customer_Id(
                        formIds, incidentId, customerId);
        if (formCount != formIds.size()) {
            throw new IllegalArgumentException("formVersionIds must belong to the customer incident.");
        }

        return responseRepository.save(new SupplementResponse(
                supplementRequest, customerId, request.message(), toJson(evidenceIds), toJson(formIds),
                LocalDateTime.now(), idempotencyKey, requestHash));
    }

    private void requireIds(Long customerId, Long incidentId, Long supplementRequestId) {
        if (customerId == null || incidentId == null || supplementRequestId == null) {
            throw new IllegalArgumentException("customerId, incidentId and supplementRequestId are required.");
        }
    }

    private List<Long> normalizedIds(List<Long> ids, String fieldName) {
        if (ids == null) return List.of();
        if (ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException(fieldName + " must contain positive IDs.");
        }
        List<Long> normalized = new ArrayList<>(ids);
        normalized.sort((left, right) -> Long.compare(left, right));
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicates.");
        }
        return normalized;
    }

    private String hashRequest(String message, List<Long> evidenceIds, List<Long> formIds) {
        return sha256(String.valueOf(message) + "\n" + evidenceIds + "\n" + formIds);
    }

    private String toJson(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
