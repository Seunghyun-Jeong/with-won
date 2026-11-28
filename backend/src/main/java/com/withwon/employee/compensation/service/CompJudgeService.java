package com.withwon.employee.compensation.service;

import static java.util.stream.Collectors.toSet;

import com.withwon.domain.transaction.CounterpartyType;
import com.withwon.domain.transaction.TransactionChannel;
import com.withwon.global.util.AccountNumbers;
import com.withwon.integration.ai.dto.CheckItem;
import com.withwon.integration.ai.dto.CheckItem.JudgeBy;
import com.withwon.integration.ai.dto.CheckItem.Scope;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TagItem;
import com.withwon.integration.ai.service.AmountNormalizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * 자율배상 점검 항목 판정(직원-35 제외 사유, 36 과실의 check_result와 답과 다름). 규칙은 시트 "자율배상 점검 항목" 탭 머리말이고 LLM은 쓰지 않는다.
 * 정리(태그)는 AI가, 규칙과 계산은 이 코드가, 결정은 담당자가 한다. 상태 없이 입력만 보고 결과 dto를 돌려주며 저장하지 않는다.
 *
 * <p>판정 방식별 규칙
 * <ul>
 *   <li>DOC: 근거 검증을 통과한(VERIFIED) 태그가 있으면 해당, 없으면 확인 필요. 서류에 없다는 것은 하지 않았다는 증거가 아니라 비해당은 나오지 않는다.
 *       NEEDS_CHECK, NO_EVIDENCE 태그는 근거를 믿을 수 없어 근거로 치지 않는다.</li>
 *   <li>DOC_RECORD(EX_SELF_ORDER, 거래마다): 직접 이체한 서류 근거가 있고 그 거래에 새 기기나 원격 접속 기록이 없으면(NONE) 해당,
 *       기록이 있는데(REMOTE_OR_NEW_DEVICE) 근거가 없으면 비해당, 나머지는 확인 필요.
 *       기록 UNKNOWN은 "기록 없음"으로 치지 않는다. 기록이 없는 것과 아직 모르는 것을 같게 두면 950만원 같은 원격 이체도 근거만 있으면 해당이 된다.</li>
 *   <li>MANUAL: 신청서 답 말고는 늘 확인 필요. 담당자가 판단한다.</li>
 *   <li>DATA(EX_FACE, EX_CARD, EX_EFIN, EX_WARNED, EX_PRIOR_RELIEF): 거래 사실만으로 정하고 태그와 신청서 답은 쓰지 않는다. 모르는 값(null, UNKNOWN)은
 *       기본값으로 넘기지 않고 확인 필요다. 다른 사건 접수일 목록이 null이면 아직 안 본 것이고 빈 목록이면 다른 사건이 없는 것이다. 규칙이 없는 코드는 예외다.</li>
 * </ul>
 *
 * <p>신청서 답: 해당이 되는 답(hitAnswers)은 검증된 서류 근거와 같게 본다. 해당이 아닌 답, 모름, 답 없음은 판정을 바꾸지 않는다.
 * 답과 다름(answerMismatch)은 신청서 답을 뺀 판정과 답이 해당과 비해당으로 서로 반대일 때다. 확인 필요는 반대가 아니고,
 * 모름과 없음은 반대 판단에 쓰지 않는다. 그래서 LLM에는 답을 안 보내고(TaggingService) 여기서 견준다.
 * 거래 단위 항목의 사건 단위 답("사고 이체를 내가 직접 했다")은 시트 규칙대로 사건의 모든 거래에 서류 근거로 친다.
 *
 * <p>거래 단위 태그는 근거에 쓴 계좌 이름표(ACCOUNT)를 대응표로 실제 계좌로 바꿔 숫자만 비교해 거래에 잇고,
 * 글에 금액이 있으면 그 금액인 거래만 남긴다. 정확히 한 거래가 남을 때만 잇는다. 태그 글이 "안전계좌"처럼 이름표를 빼기도 하고
 * 계좌가 같아도 거래가 여럿일 수 있어, 못 이으면 아무 거래에도 붙이지 않고 unlinkedTags로 따로 낸다(엉뚱한 거래의 근거가 되는 것보다 낫다).
 */
@Service
public class CompJudgeService {

    public enum Status { APPLIES, NOT_APPLIES, NEEDS_CHECK }

    /** 거래에 새 기기나 원격 접속 기록이 있는지. 모르면 UNKNOWN이고 없음(NONE)과 다르다. */
    public enum AccessRecord { REMOTE_OR_NEW_DEVICE, NONE, UNKNOWN }

    /** 그 거래의 경고 안내에 고객이 정상 거래라고 답했는지. 모르면 UNKNOWN이고 경고 없음(NONE)과 다르다. bank_security_events(직원-20)를 판정 입력으로 요약한 값이라 DB 값 목록이 아니다. */
    public enum WarningRecord { CLAIMED_NORMAL, NONE, UNKNOWN }

    /** channel, counterpartyType, warning이 null이면 모른다는 뜻이다. */
    public record CaseTx(Long id, String counterpartyAccount, BigDecimal amount, LocalDateTime occurredAt,
                         TransactionChannel channel, CounterpartyType counterpartyType, AccessRecord access, WarningRecord warning) {
    }

    /** 결과 한 줄(check_result 한 행). txId는 사건 단위 항목이면 null. tags는 이 줄에 쓴 검증된 태그, answer와 access는 쓴 신청서 답과 기록 값(없으면 null). */
    public record Line(String itemCode, Long txId, Status status, boolean answerMismatch, List<TagItem> tags,
                       String answer, AccessRecord access) {
    }

    public record Result(List<Line> lines, List<TagItem> unlinkedTags) {
    }

    private enum AnswerSide { HIT, MISS, NONE }

    public Result judge(List<CheckItem> items, List<TagItem> tags, Map<String, String> answers, List<CaseTx> txs,
                        List<MaskLabel> labels, List<LocalDateTime> otherIncidentSubmittedAt) {
        List<TagItem> verified = tags.stream().filter(t -> t.finding().status() == EvidenceStatus.VERIFIED).toList();
        List<Line> lines = new ArrayList<>();
        List<TagItem> unlinked = new ArrayList<>();
        for (CheckItem item : items) {
            if (item.judgeBy() == JudgeBy.DATA) {
                // 거래 사실만으로 정해서 태그와 신청서 답은 쓰지 않는다(답과 다름도 없다).
                if (item.scope() == Scope.INCIDENT) {
                    lines.add(dataLine(item, null, incidentDataStatus(item.code(), txs, otherIncidentSubmittedAt)));
                } else {
                    txs.forEach(tx -> lines.add(dataLine(item, tx, dataStatus(item.code(), tx))));
                }
                continue;
            }
            // 담당자 항목은 태그를 받지 않아 근거는 신청서 답뿐이다.
            List<TagItem> own = item.judgeBy() == JudgeBy.MANUAL ? List.of()
                    : verified.stream().filter(t -> t.itemCode().equals(item.code())).toList();
            String answer = answers.get(item.code());
            if (item.scope() == Scope.INCIDENT) {
                lines.add(line(item, null, own, answer, null));
                continue;
            }
            Map<Long, List<TagItem>> linked = new HashMap<>();
            for (TagItem tag : own) {
                List<CaseTx> found = txsOf(tag, txs, labels);
                if (found.size() == 1) {
                    linked.computeIfAbsent(found.getFirst().id(), k -> new ArrayList<>()).add(tag);
                } else {
                    unlinked.add(tag);
                }
            }
            for (CaseTx tx : txs) {
                lines.add(line(item, tx, linked.getOrDefault(tx.id(), List.of()), answer, tx.access()));
            }
        }
        return new Result(lines, unlinked);
    }

    private static Line dataLine(CheckItem item, CaseTx tx, Status status) {
        return new Line(item.code(), tx == null ? null : tx.id(), status, false, List.of(), null, null);
    }

    private static Status dataStatus(String code, CaseTx tx) {
        return switch (code) {
            case "EX_FACE" -> tx.channel() == null ? Status.NEEDS_CHECK
                    : yes(tx.channel() == TransactionChannel.BRANCH || tx.channel() == TransactionChannel.ATM);
            case "EX_CARD" -> tx.channel() == null ? Status.NEEDS_CHECK : yes(tx.channel() == TransactionChannel.CARD);
            // 카드 결제는 받는 곳이 비어 여기서 확인 필요가 나오지만 EX_CARD가 해당이라 그대로 둔다.
            case "EX_EFIN" -> tx.counterpartyType() == null ? Status.NEEDS_CHECK : yes(tx.counterpartyType() == CounterpartyType.EFIN);
            case "EX_WARNED" -> tx.warning() == null || tx.warning() == WarningRecord.UNKNOWN ? Status.NEEDS_CHECK
                    : yes(tx.warning() == WarningRecord.CLAIMED_NORMAL);
            default -> throw new IllegalArgumentException("판정 규칙이 없는 데이터 항목: " + code);
        };
    }

    private static Status incidentDataStatus(String code, List<CaseTx> txs, List<LocalDateTime> others) {
        if (!code.equals("EX_PRIOR_RELIEF")) {
            throw new IllegalArgumentException("판정 규칙이 없는 데이터 항목: " + code);
        }
        return priorRelief(txs, others);
    }

    /** 다른 사건 접수일이 이번 첫 피해 거래보다 엄격히 앞서면 해당. 목록이나 거래가 없으면 아직 못 본 것이라 확인 필요. */
    private static Status priorRelief(List<CaseTx> txs, List<LocalDateTime> others) {
        if (others == null || txs.isEmpty()) {
            return Status.NEEDS_CHECK;
        }
        LocalDateTime first = txs.stream().map(CaseTx::occurredAt).min(LocalDateTime::compareTo).orElseThrow();
        return yes(others.stream().anyMatch(t -> t.isBefore(first)));
    }

    private static Status yes(boolean applies) {
        return applies ? Status.APPLIES : Status.NOT_APPLIES;
    }

    private static Line line(CheckItem item, CaseTx tx, List<TagItem> evidence, String answer, AccessRecord access) {
        AnswerSide side = side(item, answer);
        Status base = status(item.judgeBy(), !evidence.isEmpty(), access);
        Status judged = side == AnswerSide.HIT ? status(item.judgeBy(), true, access) : base;
        boolean mismatch = (base == Status.APPLIES && side == AnswerSide.MISS) || (base == Status.NOT_APPLIES && side == AnswerSide.HIT);
        return new Line(item.code(), tx == null ? null : tx.id(), judged, mismatch, evidence, answer, access);
    }

    /** docEvidence는 검증된 서류 태그나 해당 답이 있는지. MANUAL은 해당 답만 근거다. */
    private static Status status(JudgeBy judgeBy, boolean docEvidence, AccessRecord access) {
        return switch (judgeBy) {
            case DOC -> docEvidence ? Status.APPLIES : Status.NEEDS_CHECK;
            case DOC_RECORD -> switch (access) {
                case NONE -> docEvidence ? Status.APPLIES : Status.NEEDS_CHECK;
                case REMOTE_OR_NEW_DEVICE -> docEvidence ? Status.NEEDS_CHECK : Status.NOT_APPLIES;
                case UNKNOWN -> Status.NEEDS_CHECK;
            };
            case MANUAL -> docEvidence ? Status.APPLIES : Status.NEEDS_CHECK;
            case DATA -> throw new IllegalStateException("데이터 항목은 dataStatus로 판정한다");
        };
    }

    private static AnswerSide side(CheckItem item, String answer) {
        if (answer == null || answer.equals("UNKNOWN")) {
            return AnswerSide.NONE;
        }
        return item.hitAnswers().contains(answer) ? AnswerSide.HIT : AnswerSide.MISS;
    }

    /** 태그의 글과 근거 인용문에 쓴 계좌 이름표와 금액에 맞는 거래들. 이름표가 없으면 비어 있다. */
    private static List<CaseTx> txsOf(TagItem tag, List<CaseTx> txs, List<MaskLabel> labels) {
        List<String> texts = Stream.concat(Stream.of(tag.finding().text()),
                tag.finding().evidence().stream().map(Evidence::quote)).toList();
        Set<String> accounts = labels.stream().filter(l -> l.kind() == MaskLabel.Kind.ACCOUNT)
                .filter(l -> texts.stream().anyMatch(t -> t.contains(l.label())))
                .map(l -> AccountNumbers.digits(l.value())).collect(toSet());
        Set<Long> amounts = texts.stream().flatMap(t -> AmountNormalizer.find(t).stream())
                .map(AmountNormalizer.Amount::won).collect(toSet());
        return txs.stream()
                .filter(tx -> accounts.contains(AccountNumbers.digits(tx.counterpartyAccount())))
                .filter(tx -> amounts.isEmpty() || amounts.stream().anyMatch(w -> tx.amount().compareTo(BigDecimal.valueOf(w)) == 0))
                .toList();
    }
}
