package com.withwon.domain.supplement;

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
@Table(name = "supplement_responses")
public class SupplementResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplement_request_id", nullable = false)
    private SupplementRequest supplementRequest;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "evidence_version_ids", columnDefinition = "json")
    private String evidenceVersionIds;

    @Column(name = "form_version_ids", columnDefinition = "json")
    private String formVersionIds;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "request_key", nullable = false, unique = true, length = 100)
    private String requestKey;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    protected SupplementResponse() {
    }

    public SupplementResponse(SupplementRequest supplementRequest, Long customerId, String message,
                              String evidenceVersionIds, String formVersionIds,
                              LocalDateTime submittedAt, String requestKey, String requestHash) {
        this.supplementRequest = supplementRequest;
        this.customerId = customerId;
        this.message = message;
        this.evidenceVersionIds = evidenceVersionIds;
        this.formVersionIds = formVersionIds;
        this.submittedAt = submittedAt;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
    }

    public Long getId() { return id; }
    public Long getSupplementRequestId() { return supplementRequest.getId(); }
    public Long getCustomerId() { return customerId; }
    public String getMessage() { return message; }
    public String getEvidenceVersionIds() { return evidenceVersionIds; }
    public String getFormVersionIds() { return formVersionIds; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getRequestKey() { return requestKey; }
    public String getRequestHash() { return requestHash; }
}
