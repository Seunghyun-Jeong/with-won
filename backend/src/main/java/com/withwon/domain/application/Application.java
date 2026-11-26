package com.withwon.domain.application;

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
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "application_type", nullable = false, length = 24)
    private String applicationType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "payment_stop_at")
    private LocalDateTime paymentStopAt;

    @Column(name = "initial_due_at")
    private LocalDateTime initialDueAt;

    @Column(name = "extended_due_at")
    private LocalDateTime extendedDueAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "submit_key", length = 100)
    private String submitKey;

    @Column(name = "submit_request_hash", length = 64)
    private String submitRequestHash;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected Application() {
    }

    public Application(Incident incident, String applicationType, String status) {
        this.incident = incident;
        this.applicationType = applicationType;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public String getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
