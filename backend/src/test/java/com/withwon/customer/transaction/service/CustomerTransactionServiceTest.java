package com.withwon.customer.transaction.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import com.withwon.domain.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerTransactionServiceTest {

    private IncidentRepository incidentRepository;
    private TransactionRepository transactionRepository;
    private CustomerTransactionService service;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        service = new CustomerTransactionService(incidentRepository, transactionRepository);
    }

    @Test
    void searchesTransactionsOwnedByCustomerWithInclusiveDateRange() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.of(mock(Incident.class)));
        when(transactionRepository.searchCustomerTransactions(
                7L, 11L, 22L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 1, 0, 0),
                PageRequest.of(1, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 0));

        var result = service.findAvailableTransactions(
                7L, 11L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                22L, 1, 20);

        assertEquals(0, result.getTotalElements());
        verify(transactionRepository).searchCustomerTransactions(
                7L, 11L, 22L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 1, 0, 0),
                PageRequest.of(1, 20));
    }

    @Test
    void rejectsIncidentOwnedByAnotherCustomer() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class,
                () -> service.findAvailableTransactions(
                        7L, 11L, null, null, null, 0, 20));
    }

    @Test
    void rejectsReversedDateRange() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L))
                .thenReturn(Optional.of(mock(Incident.class)));

        assertThrows(IllegalArgumentException.class,
                () -> service.findAvailableTransactions(
                        7L, 11L,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1),
                        null, 0, 20));
    }
}
