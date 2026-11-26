package com.withwon.domain.supplement;

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
@Table(name = "supplement_requests")
public class SupplementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "refund_target", nullable = false)
    private boolean refundTarget;

    @Column(name = "compensation_target", nullable = false)
    private boolean compensationTarget;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "required_items", columnDefinition = "json")
    private String requiredItems;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected SupplementRequest() {
    }

    public Long getId() {
        return id;
    }

    public boolean isRefundTarget() {
        return refundTarget;
    }

    public boolean isCompensationTarget() {
        return compensationTarget;
    }

    public String getMessage() {
        return message;
    }

    public String getRequiredItems() {
        return requiredItems;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
