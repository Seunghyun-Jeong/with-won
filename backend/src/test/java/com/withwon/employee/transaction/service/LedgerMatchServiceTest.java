package com.withwon.employee.transaction.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.customer.Customer;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import com.withwon.domain.transaction.OutgoingTransactionProjection;
import com.withwon.domain.transaction.TransactionRepository;
import com.withwon.employee.transaction.service.LedgerMatchService.Candidate;
import com.withwon.employee.transaction.service.LedgerMatchService.Grade;
import com.withwon.employee.transaction.service.LedgerMatchService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 김우리 사건(정답.json 사기거래)의 원장으로 대조 규칙을 본다. */
class LedgerMatchServiceTest {

    private static final long KIM_WOORI = 7L;
    private static final long INCIDENT = 70L;

    // 원장은 초까지 있고, 은행 이름과 계좌 모양은 서류와 다르게 적혀 있다.
    private static final Row TX_300 = new Row(1L, won(3_000_000), at(14, 32, 7), "국민은행", "48290101336417");
    private static final Row TX_950 = new Row(2L, won(9_500_000), at(15, 10, 41), "신한은행", "110472836150");
    private static final Row SAME_AMOUNT_OTHER = new Row(3L, won(3_000_000), at(14, 50, 0), "하나은행", "35791002468013");
    private static final Row SMALL = new Row(4L, won(12_000), at(12, 5, 0), "우리은행", "1002111222333");
    private static final List<OutgoingTransactionProjection> LEDGER = List.of(SMALL, TX_300, SAME_AMOUNT_OTHER, TX_950);

    private IncidentRepository incidents;
    private TransactionRepository transactions;
    private LedgerMatchService service;

    @BeforeEach
    void setUp() {
        incidents = mock(IncidentRepository.class);
        transactions = mock(TransactionRepository.class);
        service = new LedgerMatchService(incidents, transactions);

        Customer kim = mock(Customer.class);
        when(kim.getId()).thenReturn(KIM_WOORI);
        Incident incident = mock(Incident.class);
        when(incident.getCustomer()).thenReturn(kim);
        when(incidents.findById(INCIDENT)).thenReturn(Optional.of(incident));
        // 김우리 계좌 거래만 돌려준다. 다른 고객 번호로 물으면 Mockito 기본값인 빈 목록이 온다.
        when(transactions.findOutgoingByCustomer(eq(KIM_WOORI), any(), any())).thenReturn(LEDGER);
    }

    @Test
    void 신청서의_300만원_송금은_원장의_같은_거래가_일치로_먼저_나온다() {
        var claim = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 32, 0), "KB국민은행", "482901-01-336417");

        Candidate first = service.match(INCIDENT, List.of(claim)).claims().getFirst().candidates().getFirst();

        assertEquals(1L, first.row().getTransactionId());
        assertEquals(Grade.MATCH, first.grade());
    }

    @Test
    void 금액만_같고_계좌가_다른_거래는_부분_일치로_뒤에_나온다() {
        var claim = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 32, 0), "KB국민은행", "482901-01-336417");

        List<Candidate> candidates = service.match(INCIDENT, List.of(claim)).claims().getFirst().candidates();

        assertEquals(List.of(1L, 3L), candidates.stream().map(c -> c.row().getTransactionId()).toList());
        Candidate other = candidates.get(1);
        assertEquals(Grade.PARTIAL, other.grade());
        assertTrue(other.amountMatch());
        assertFalse(other.accountMatch());
        assertFalse(other.timeMatch());
    }

    @Test
    void 서류의_시각이_원장과_다르면_같은_거래도_부분_일치로_시각_다름이_보인다() {
        var claim = new ClaimedTransfer("사건사고사실확인원", won(3_000_000), at(14, 40, 0), "KB국민은행", "482901-01-336417");

        Candidate first = service.match(INCIDENT, List.of(claim)).claims().getFirst().candidates().getFirst();

        assertEquals(1L, first.row().getTransactionId());
        assertEquals(Grade.PARTIAL, first.grade());
        assertFalse(first.timeMatch());
        assertTrue(first.amountMatch() && first.bankMatch() && first.accountMatch());
    }

    @Test
    void 피해자가_하지_않았다고_한_950만원도_후보로_나온다() {
        var claim = new ClaimedTransfer("피해구제신청서", won(9_500_000), at(15, 10, 0), "신한은행", "110-472-836150");

        Candidate first = service.match(INCIDENT, List.of(claim)).claims().getFirst().candidates().getFirst();

        assertEquals(2L, first.row().getTransactionId());
        assertEquals(Grade.MATCH, first.grade());
    }

    @Test
    void 다른_고객의_사건이면_김우리_거래는_후보에_나오지_않는다() {
        Customer other = mock(Customer.class);
        when(other.getId()).thenReturn(8L);
        Incident otherIncident = mock(Incident.class);
        when(otherIncident.getCustomer()).thenReturn(other);
        when(incidents.findById(80L)).thenReturn(Optional.of(otherIncident));
        var claim = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 32, 0), "KB국민은행", "482901-01-336417");

        Result result = service.match(80L, List.of(claim));

        assertTrue(result.claims().getFirst().candidates().isEmpty());
        assertTrue(result.ledger().isEmpty());
    }

    @Test
    void 맞는_후보가_없어도_직원이_고를_원장_목록은_남는다() {
        var claim = new ClaimedTransfer("피해구제신청서", won(5_000_000), at(18, 0, 0), "농협은행", "302-1234-5678-91");

        Result result = service.match(INCIDENT, List.of(claim));

        assertTrue(result.claims().getFirst().candidates().isEmpty());
        assertEquals(LEDGER, result.ledger());
    }

    @Test
    void 시각_차이가_30분까지는_후보이고_넘으면_빠진다() {
        var at30 = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 2, 7), "KB국민은행", "482901-01-336417");
        var over30 = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 2, 6), "KB국민은행", "482901-01-336417");

        Result result = service.match(INCIDENT, List.of(at30, over30));

        assertEquals(List.of(1L), result.claims().get(0).candidates().stream().map(c -> c.row().getTransactionId()).toList());
        assertTrue(result.claims().get(1).candidates().isEmpty());
    }

    @Test
    void 대조할_송금이_없으면_빈_결과다() {
        Result result = service.match(INCIDENT, List.of());

        assertTrue(result.claims().isEmpty());
        assertTrue(result.ledger().isEmpty());
    }

    @Test
    void 서류에도_원장에도_계좌가_없으면_계좌_일치로_치지_않는다() {
        Row atm = new Row(5L, won(12_000), at(12, 5, 0), null, null);
        when(transactions.findOutgoingByCustomer(eq(KIM_WOORI), any(), any())).thenReturn(List.of(atm));
        var claim = new ClaimedTransfer("피해구제신청서", won(12_000), at(12, 5, 0), null, null);

        Candidate first = service.match(INCIDENT, List.of(claim)).claims().getFirst().candidates().getFirst();

        assertEquals(Grade.PARTIAL, first.grade());
        assertFalse(first.bankMatch() || first.accountMatch());
    }

    @Test
    void 없는_사건이면_막는다() {
        var claim = new ClaimedTransfer("피해구제신청서", won(3_000_000), at(14, 32, 0), "KB국민은행", "482901-01-336417");

        assertThrows(IncidentNotFoundException.class, () -> service.match(999L, List.of(claim)));
    }

    private static BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }

    private static LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(2026, 11, 18, hour, minute, second);
    }

    private record Row(Long getTransactionId, BigDecimal getAmount, LocalDateTime getOccurredAt,
                       String getCounterpartyBank, String getCounterpartyAccount)
            implements OutgoingTransactionProjection {
    }
}
