package com.withwon.domain.evidence;

import com.withwon.domain.incident.Incident;
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
@Table(name = "evidence_files")
public class EvidenceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "refund_target", nullable = false)
    private boolean refundTarget;

    @Column(name = "compensation_target", nullable = false)
    private boolean compensationTarget;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "classification_source", nullable = false, length = 24)
    private String classificationSource;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected EvidenceFile() {
    }

    public EvidenceFile(Incident incident, String documentType, boolean refundTarget,
                        boolean compensationTarget, String status, String classificationSource) {
        this.incident = incident;
        this.documentType = documentType;
        this.refundTarget = refundTarget;
        this.compensationTarget = compensationTarget;
        this.status = status;
        this.classificationSource = classificationSource;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getDocumentType() {
        return documentType;
    }

    public boolean isRefundTarget() {
        return refundTarget;
    }

    public boolean isCompensationTarget() {
        return compensationTarget;
    }

    public String getStatus() {
        return status;
    }

    public String getClassificationSource() {
        return classificationSource;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
