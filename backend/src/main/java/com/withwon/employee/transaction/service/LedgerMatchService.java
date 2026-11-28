package com.withwon.employee.transaction.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.incident.IncidentRepository;
import com.withwon.domain.transaction.OutgoingTransactionProjection;
import com.withwon.domain.transaction.TransactionRepository;
import com.withwon.global.util.AccountNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 거래 기록 대조(직원-27). 서류가 말하는 송금을 사건 고객의 원장 출금과 규칙으로 맞춰 후보를 보이고, 확정은 직원이 한다.
 * LLM은 쓰지 않는다. 서류끼리 다른 금액, 시각, 받은 계좌도 서류마다 따로 대조한 결과로 드러난다(DOC_CHECK와 요약은 이것을 짚지 않는다).
 *
 * <p>규칙
 * <ul>
 *   <li>금액은 정확히 같아야 일치. 시각은 서류가 분 단위라 같은 분이면 일치.</li>
 *   <li>계좌는 숫자만 비교. 은행은 공백, "은행", 앞 영문 약칭(KB, NH, IBK, SC, KEB)을 떼고 비교.</li>
 *   <li>후보는 시각 차이 30분 안에서 금액이나 계좌 중 하나라도 맞는 거래. 서류 시각은 피해자 기억이나 캡처라 몇 분 어긋날 수 있고,
 *       30분을 넘기면 같은 날 다른 송금과 헷갈릴 수 있어 이 폭으로 정했다.</li>
 *   <li>네 칸이 모두 맞으면 MATCH, 아니면 PARTIAL. 칸마다 맞았는지를 같이 내어 직원이 어디가 다른지 본다.</li>
 *   <li>예금주는 원장(transactions)에 칸이 없어 대조하지 않는다. 누가 이체했는지(직접, 원격)도 보지 않는다(직원-35 몫).</li>
 * </ul>
 * 원장 목록은 서류 날짜들의 하루 단위로 돌려주어, 맞는 후보가 없을 때 직원이 직접 고른다.
 */
@Service
public class LedgerMatchService {

    private static final Duration WINDOW = Duration.ofMinutes(30);

    public enum Grade { MATCH, PARTIAL }

    public record Candidate(OutgoingTransactionProjection row, Grade grade,
                            boolean amountMatch, boolean timeMatch, boolean bankMatch, boolean accountMatch) {

        private int matchedCount() {
            return (amountMatch ? 1 : 0) + (timeMatch ? 1 : 0) + (bankMatch ? 1 : 0) + (accountMatch ? 1 : 0);
        }
    }

    public record ClaimMatch(ClaimedTransfer claim, List<Candidate> candidates) {
    }

    public record Result(List<ClaimMatch> claims, List<OutgoingTransactionProjection> ledger) {
    }

    private final IncidentRepository incidents;
    private final TransactionRepository transactions;

    public LedgerMatchService(IncidentRepository incidents, TransactionRepository transactions) {
        this.incidents = incidents;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Result match(Long incidentId, List<ClaimedTransfer> claims) {
        Long customerId = incidents.findById(incidentId)
                .orElseThrow(IncidentNotFoundException::new)
                .getCustomer().getId();
        if (claims.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        List<LocalDateTime> times = claims.stream().map(ClaimedTransfer::at).sorted().toList();
        LocalDateTime from = times.getFirst().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime to = times.getLast().truncatedTo(ChronoUnit.DAYS).plusDays(1);
        List<OutgoingTransactionProjection> ledger = transactions.findOutgoingByCustomer(customerId, from, to);

        List<ClaimMatch> matches = claims.stream()
                .map(claim -> new ClaimMatch(claim, candidates(claim, ledger)))
                .toList();
        return new Result(matches, ledger);
    }

    private static List<Candidate> candidates(ClaimedTransfer claim, List<OutgoingTransactionProjection> ledger) {
        return ledger.stream()
                .filter(row -> gap(claim, row).compareTo(WINDOW) <= 0)
                .map(row -> compare(claim, row))
                .filter(c -> c.amountMatch() || c.accountMatch())
                .sorted(Comparator.comparing(Candidate::grade)
                        .thenComparing(Comparator.comparingInt(Candidate::matchedCount).reversed())
                        .thenComparing(c -> gap(claim, c.row())))
                .toList();
    }

    private static Candidate compare(ClaimedTransfer claim, OutgoingTransactionProjection row) {
        boolean amount = claim.amount().compareTo(row.getAmount()) == 0;
        boolean time = claim.at().truncatedTo(ChronoUnit.MINUTES)
                .equals(row.getOccurredAt().truncatedTo(ChronoUnit.MINUTES));
        boolean bank = sameKnown(bank(claim.bank()), bank(row.getCounterpartyBank()));
        boolean account = sameKnown(AccountNumbers.digits(claim.account()), AccountNumbers.digits(row.getCounterpartyAccount()));
        Grade grade = amount && time && bank && account ? Grade.MATCH : Grade.PARTIAL;
        return new Candidate(row, grade, amount, time, bank, account);
    }

    /** 양쪽 다 비어 있으면(ATM 출금, 서류에 안 적힘) 같은 것으로 치지 않는다. */
    private static boolean sameKnown(String a, String b) {
        return a != null && !a.isEmpty() && a.equals(b);
    }

    private static Duration gap(ClaimedTransfer claim, OutgoingTransactionProjection row) {
        return Duration.between(claim.at(), row.getOccurredAt()).abs();
    }

    private static String bank(String name) {
        return name == null ? null : name.replaceAll("\\s", "").replace("은행", "").replaceFirst("^[A-Z]+", "");
    }
}
