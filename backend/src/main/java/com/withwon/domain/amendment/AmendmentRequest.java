package com.withwon.domain.amendment;

import com.withwon.domain.application.Application;
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
@Table(name = "amendment_requests")
public class AmendmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(name = "request_type", nullable = false, length = 32)
    private String requestType;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "change_data", nullable = false, columnDefinition = "json")
    private String changeData;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected AmendmentRequest() {
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getChangeData() {
        return changeData;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
