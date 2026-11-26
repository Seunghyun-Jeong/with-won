package com.withwon.domain.application;

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
@Table(name = "application_form_versions")
public class ApplicationFormVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "version_type", nullable = false, length = 24)
    private String versionType;

    @Column(name = "form_data", nullable = false, columnDefinition = "json")
    private String formData;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected ApplicationFormVersion() {
    }

    public Long getId() {
        return id;
    }

    public ApplicationFormVersion(Application application, Integer versionNo, String versionType,
                                  String formData, String schemaVersion) {
        this.application = application;
        this.versionNo = versionNo;
        this.versionType = versionType;
        this.formData = formData;
        this.schemaVersion = schemaVersion;
    }

    public Integer getVersionNo() {
        return versionNo;
    }
}
