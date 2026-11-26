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

import java.time.LocalDateTime;

@Entity
@Table(name = "application_consents")
public class ApplicationConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id")
    private ApplicationFormVersion formVersion;

    @Column(name = "consent_type", nullable = false, length = 32)
    private String consentType;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "policy_text", nullable = false, columnDefinition = "text")
    private String policyText;

    protected ApplicationConsent() {
    }

    public ApplicationConsent(Incident incident, String consentType, String policyVersion,
                              LocalDateTime agreedAt, String policyText) {
        this.incident = incident;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.agreedAt = agreedAt;
        this.policyText = policyText;
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public ApplicationFormVersion getFormVersion() {
        return formVersion;
    }

    public String getConsentType() {
        return consentType;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }

    public String getPolicyText() {
        return policyText;
    }
}
