package com.withwon.customer.incident.controller;

import com.withwon.domain.refund.RefundResultRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerIncidentRefundResponse(
        Long refundResultId,
        String fraudBankCode,
        String maskedAccountNumber,
        BigDecimal refundAmount,
        LocalDate noticeDate,
        Integer versionNo
) {

    public static CustomerIncidentRefundResponse from(RefundResultRecord record) {
        return new CustomerIncidentRefundResponse(
                record.getId(),
                record.getFraudBankCode(),
                maskAccountNumber(record.getFraudAccountNumber()),
                record.getRefundAmount(),
                record.getNoticeDate(),
                record.getVersionNo());
    }

    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }
}
