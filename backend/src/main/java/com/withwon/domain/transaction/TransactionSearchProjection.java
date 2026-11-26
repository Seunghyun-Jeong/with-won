package com.withwon.domain.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionSearchProjection {

    Long getTransactionId();

    BigDecimal getAmount();

    LocalDateTime getTransactedAt();

    String getMaskedRecipient();

    Boolean getSelectable();
}
