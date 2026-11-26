package com.withwon.customer.transaction.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.incident.IncidentRepository;
import com.withwon.domain.transaction.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CustomerTransactionService {

    private final IncidentRepository incidentRepository;
    private final TransactionRepository transactionRepository;

    public CustomerTransactionService(IncidentRepository incidentRepository,
                                      TransactionRepository transactionRepository) {
        this.incidentRepository = incidentRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<com.withwon.domain.transaction.TransactionSearchProjection> findAvailableTransactions(
            Long customerId, Long incidentId, LocalDate startDate, LocalDate endDate,
            Long accountId, int page, int size) {
        incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate.");
        }
        return transactionRepository.searchCustomerTransactions(
                customerId,
                incidentId,
                accountId,
                startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size));
    }
}
