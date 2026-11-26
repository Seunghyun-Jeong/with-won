package com.withwon.domain.refund;

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

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "refund_result_records")
public class RefundResultRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "fraud_bank_code", nullable = false, length = 20)
    private String fraudBankCode;

    @Column(name = "fraud_account_number", nullable = false, length = 40)
    private String fraudAccountNumber;

    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal refundAmount;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    protected RefundResultRecord() {
    }

    public Long getId() {
        return id;
    }

    public String getFraudBankCode() {
        return fraudBankCode;
    }

    public String getFraudAccountNumber() {
        return fraudAccountNumber;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public LocalDate getNoticeDate() {
        return noticeDate;
    }

    public Integer getVersionNo() {
        return versionNo;
    }
}
