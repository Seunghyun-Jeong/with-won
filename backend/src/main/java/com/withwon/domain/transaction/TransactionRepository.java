package com.withwon.domain.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {

    @Query(value = """
            SELECT t.id AS transactionId,
                   t.amount AS amount,
                   t.occurred_at AS transactedAt,
                   CASE WHEN t.counterparty_account IS NULL THEN NULL
                        ELSE CONCAT('****', RIGHT(t.counterparty_account, 4)) END AS maskedRecipient,
                   CASE WHEN NOT EXISTS (
                            SELECT 1 FROM incident_transactions selected
                            WHERE selected.incident_id = :incidentId
                              AND selected.transaction_id = t.id
                        ) THEN TRUE ELSE FALSE END AS selectable
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE a.customer_id = :customerId
              AND a.active = TRUE
              AND (:accountId IS NULL OR a.id = :accountId)
              AND (:startDate IS NULL OR t.occurred_at >= :startDate)
              AND (:endDate IS NULL OR t.occurred_at < :endDate)
            ORDER BY t.occurred_at DESC, t.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE a.customer_id = :customerId
              AND a.active = TRUE
              AND (:accountId IS NULL OR a.id = :accountId)
              AND (:startDate IS NULL OR t.occurred_at >= :startDate)
              AND (:endDate IS NULL OR t.occurred_at < :endDate)
            """,
            nativeQuery = true)
    Page<TransactionSearchProjection> searchCustomerTransactions(
            @Param("customerId") Long customerId,
            @Param("incidentId") Long incidentId,
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // 직원-27 대조와 직원이 고를 목록. 해지한 계좌의 지난 거래도 피해 거래일 수 있어 active로 거르지 않는다.
    // direction 값 'OUT'은 ERD ledger_tx.direction(OUT 출금 / IN 입금)을 따른다.
    @Query(value = """
            SELECT t.id AS transactionId,
                   t.amount AS amount,
                   t.occurred_at AS occurredAt,
                   t.counterparty_bank AS counterpartyBank,
                   t.counterparty_account AS counterpartyAccount
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE a.customer_id = :customerId
              AND t.direction = 'OUT'
              AND t.occurred_at >= :from
              AND t.occurred_at < :to
            ORDER BY t.occurred_at, t.id
            """, nativeQuery = true)
    List<OutgoingTransactionProjection> findOutgoingByCustomer(
            @Param("customerId") Long customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
