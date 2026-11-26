package com.withwon.customer.transaction.controller;

import com.withwon.domain.transaction.TransactionSearchProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerTransactionResponse(
        Long transactionId,
        BigDecimal amount,
        LocalDateTime transactedAt,
        String maskedRecipient,
        boolean selectable) {

    public static CustomerTransactionResponse from(TransactionSearchProjection projection) {
        return new CustomerTransactionResponse(
                projection.getTransactionId(),
                projection.getAmount(),
                projection.getTransactedAt(),
                projection.getMaskedRecipient(),
                Boolean.TRUE.equals(projection.getSelectable()));
    }
}
