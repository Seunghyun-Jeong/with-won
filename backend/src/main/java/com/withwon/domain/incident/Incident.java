package com.withwon.domain.incident;

import com.withwon.domain.customer.Customer;
import com.withwon.domain.employee.Employee;
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
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @Column(name = "incident_number", nullable = false, unique = true, length = 40)
    private String incidentNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "recognized_at")
    private LocalDateTime recognizedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "submit_key", length = 100)
    private String submitKey;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "submit_request_hash", length = 64)
    private String submitRequestHash;

    protected Incident() {
    }

    public Incident(Customer customer, String incidentNumber, String status, String description) {
        this.customer = customer;
        this.incidentNumber = incidentNumber;
        this.status = status;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Employee getAssignedEmployee() {
        return assignedEmployee;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getRecognizedAt() {
        return recognizedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }

    public String getSubmitKey() {
        return submitKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSubmitRequestHash() {
        return submitRequestHash;
    }

    public void setSubmitRequest(String submitKey, String submitRequestHash) {
        this.submitKey = submitKey;
        this.submitRequestHash = submitRequestHash;
    }
}
