package com.withwon.domain.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OutgoingTransactionProjection {

    Long getTransactionId();

    BigDecimal getAmount();

    LocalDateTime getOccurredAt();

    String getCounterpartyBank();

    String getCounterpartyAccount();
}
