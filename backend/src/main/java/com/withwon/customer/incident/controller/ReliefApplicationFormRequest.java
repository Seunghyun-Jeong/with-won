package com.withwon.customer.incident.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReliefApplicationFormRequest(
        ApplicantProfile applicant,
        List<TransferEntry> victimAccountTransfers,
        List<FraudAccountDeposit> fraudAccountDeposits,
        RefundAssetAccount refundAssetAccount,
        String applicationReason,
        String refundAccountNumber
) {

    public record ApplicantProfile(
            String name,
            String birthDate,
            String address,
            String phone,
            String email
    ) {
    }

    public record TransferEntry(
            String financialInstitution,
            String branch,
            String assetType,
            String accountInfo,
            String holderName,
            LocalDateTime transferredAt,
            BigDecimal amount,
            String virtualAssetTypeAndQuantity
    ) {
    }

    public record FraudAccountDeposit(
            String financialInstitution,
            String accountInfo,
            String holderName,
            LocalDateTime depositedAt,
            BigDecimal amount,
            String virtualAssetTypeAndQuantity
    ) {
    }

    public record RefundAssetAccount(
            String financialInstitution,
            String accountInfo,
            String holderName
    ) {
    }
}
