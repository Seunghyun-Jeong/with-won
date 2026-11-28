package com.withwon.employee.compensation.service;

import static com.withwon.employee.compensation.service.CompJudgeService.AccessRecord.NONE;
import static com.withwon.employee.compensation.service.CompJudgeService.AccessRecord.REMOTE_OR_NEW_DEVICE;
import static com.withwon.employee.compensation.service.CompJudgeService.AccessRecord.UNKNOWN;
import static com.withwon.employee.compensation.service.CompJudgeService.Status.APPLIES;
import static com.withwon.employee.compensation.service.CompJudgeService.Status.NEEDS_CHECK;
import static com.withwon.employee.compensation.service.CompJudgeService.Status.NOT_APPLIES;
import static com.withwon.integration.ai.TestFiles.COMP_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.withwon.employee.compensation.service.CompJudgeService.AccessRecord;
import com.withwon.employee.compensation.service.CompJudgeService.CaseTx;
import com.withwon.employee.compensation.service.CompJudgeService.Line;
import com.withwon.employee.compensation.service.CompJudgeService.Result;
import com.withwon.employee.compensation.service.CompJudgeService.Status;
import com.withwon.employee.compensation.service.CompJudgeService.WarningRecord;
import com.withwon.domain.transaction.CounterpartyType;
import com.withwon.domain.transaction.TransactionChannel;
import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.dto.CheckItem;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TagItem;
import com.withwon.integration.ai.service.KimWooriCase;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// 자율배상 판정(직원-35, 36의 check_result). 규칙은 시트 "자율배상 점검 항목" 탭 머리말이고 LLM을 쓰지 않는다.
class CompJudgeServiceTest {

    private static final List<MaskLabel> LABELS = List.of(
            new MaskLabel("[받은계좌1]", MaskLabel.Kind.ACCOUNT, "482901-01-336417"),
            new MaskLabel("[받은계좌2]", MaskLabel.Kind.ACCOUNT, "110-472-836150"));

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 10, 0);

    private final CompJudgeService service = new CompJudgeService();

    private static CaseTx tx(long id, String account, String amount, AccessRecord access) {
        return new CaseTx(id, account, new BigDecimal(amount), T0, TransactionChannel.MOBILE, CounterpartyType.BANK, access,
                WarningRecord.NONE);
    }

    private static CaseTx dataTx(TransactionChannel channel, CounterpartyType type, WarningRecord warning) {
        return new CaseTx(1L, "482901-01-336417", new BigDecimal("3000000"), T0, channel, type, NONE, warning);
    }

    private Status dataStatus(String code, CaseTx tx) {
        return service.judge(List.of(item(code)), List.of(), Map.of(), List.of(tx), LABELS, List.of()).lines().getFirst().status();
    }

    private Status priorStatus(List<LocalDateTime> others, List<CaseTx> txs) {
        return service.judge(List.of(item("EX_PRIOR_RELIEF")), List.of(), Map.of(), txs, LABELS, others).lines().getFirst().status();
    }

    private static CheckItem item(String code) {
        return COMP_ITEMS.stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow();
    }

    private static TagItem tag(String code, EvidenceStatus status, String text, String... quotes) {
        CheckItem item = item(code);
        List<Evidence> evidence = Arrays.stream(quotes).map(q -> new Evidence(1L, q, true)).toList();
        return new TagItem(code, item.kind(), new FindingText(text, text, status, evidence, List.of(), List.of()));
    }

    private static TagItem verified(String code) {
        return tag(code, EvidenceStatus.VERIFIED, "피해자가 했다", "했다");
    }

    private Line only(Result result, String code) {
        return result.lines().stream().filter(l -> l.itemCode().equals(code)).findFirst().orElseThrow();
    }

    private Result judge(List<String> codes, List<TagItem> tags, Map<String, String> answers) {
        return service.judge(codes.stream().map(CompJudgeServiceTest::item).toList(), tags, answers, List.of(), LABELS, List.of());
    }

    private Line judgeOne(String code, List<TagItem> tags, Map<String, String> answers) {
        return only(judge(List.of(code), tags, answers), code);
    }

    @Test
    void 김우리_링크_앱_개인정보_보안매체_태그는_신청서_답과_같아_해당이고_답과_다름이_아니다() throws IOException {
        KimWooriCase kim = new KimWooriCase();
        List<TagItem> tags = kim.tagRecorded().tags();
        Result result = service.judge(nonData(), tags, kimAnswers(), kimTxs(), kim.labels, List.of());

        for (String code : List.of("FT_SMISHING_LINK", "FT_MALICIOUS_APP", "FT_GAVE_PERSONAL", "FT_GAVE_SECURITY")) {
            Line line = only(result, code);
            assertThat(line.status()).as(code).isEqualTo(APPLIES);
            assertThat(line.answerMismatch()).as(code).isFalse();
            assertThat(line.txId()).isNull();
        }
        assertThat(only(result, "FT_NO_LOCK").status()).isEqualTo(NEEDS_CHECK);
        assertThat(only(result, "FT_NO_LOCK").answerMismatch()).isFalse();
        assertThat(only(result, "FT_GAVE_ID").status()).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 김우리_직접_이체는_300만원_거래만_해당이고_950만원_원격_거래는_확인_필요다() throws IOException {
        KimWooriCase kim = new KimWooriCase();
        List<TagItem> tags = kim.tagRecorded().tags();
        Result result = service.judge(nonData(), tags, kimAnswers(), kimTxs(), kim.labels, List.of());

        List<Line> lines = result.lines().stream().filter(l -> l.itemCode().equals("EX_SELF_ORDER")).toList();
        assertThat(lines).extracting(Line::txId).containsExactly(1L, 2L);
        assertThat(lines.get(0).status()).isEqualTo(APPLIES);
        assertThat(lines.get(0).tags()).extracting(TagItem::itemCode).containsExactly("EX_SELF_ORDER");
        assertThat(lines.get(0).answerMismatch()).isFalse();
        assertThat(lines.get(1).status()).isEqualTo(NEEDS_CHECK);
        assertThat(lines.get(1).tags()).isEmpty();
        assertThat(result.unlinkedTags()).isEmpty();
    }

    @Test
    void 비해당_답인데_검증된_태그가_붙으면_해당이고_답과_다름이다() {
        Line line = judgeOne("FT_GAVE_ID", List.of(verified("FT_GAVE_ID")), Map.of("FT_GAVE_ID", "NO"));

        assertThat(line.status()).isEqualTo(APPLIES);
        assertThat(line.answerMismatch()).isTrue();
    }

    @Test
    void 태그도_답도_없는_서류_항목은_확인_필요다() {
        Line line = judgeOne("FT_GAVE_ID", List.of(), Map.of());

        assertThat(line.status()).isEqualTo(NEEDS_CHECK);
        assertThat(line.answerMismatch()).isFalse();
    }

    @Test
    void 잠금_있음_답은_비해당_답이라_태그가_없으면_확인_필요이고_태그가_있으면_해당과_답과_다름이다() {
        Map<String, String> answers = Map.of("FT_NO_LOCK", "YES");

        Line none = judgeOne("FT_NO_LOCK", List.of(), answers);
        Line tagged = judgeOne("FT_NO_LOCK", List.of(verified("FT_NO_LOCK")), answers);

        assertThat(none.status()).isEqualTo(NEEDS_CHECK);
        assertThat(none.answerMismatch()).isFalse();
        assertThat(tagged.status()).isEqualTo(APPLIES);
        assertThat(tagged.answerMismatch()).isTrue();
    }

    @Test
    void 모름_답은_판정도_답과_다름도_바꾸지_않는다() {
        Line line = judgeOne("FT_GAVE_ID", List.of(verified("FT_GAVE_ID")), Map.of("FT_GAVE_ID", "UNKNOWN"));

        assertThat(line.status()).isEqualTo(APPLIES);
        assertThat(line.answerMismatch()).isFalse();
    }

    @Test
    void 근거_확인이_안_된_태그는_해당_근거로_치지_않는다() {
        for (EvidenceStatus status : List.of(EvidenceStatus.NEEDS_CHECK, EvidenceStatus.NO_EVIDENCE)) {
            Line line = judgeOne("FT_GAVE_ID", List.of(tag("FT_GAVE_ID", status, "피해자가 했다", "했다")), Map.of());

            assertThat(line.status()).as(status.name()).isEqualTo(NEEDS_CHECK);
            assertThat(line.tags()).isEmpty();
        }
    }

    @Test
    void 해당_답이면_태그가_없어도_해당이고_답과_다름이_아니다() {
        Line line = judgeOne("FT_GAVE_ID", List.of(), Map.of("FT_GAVE_ID", "YES"));

        assertThat(line.status()).isEqualTo(APPLIES);
        assertThat(line.answerMismatch()).isFalse();
        assertThat(line.answer()).isEqualTo("YES");
    }

    private Line judgeSelfOrder(AccessRecord access, boolean tagged, String answer) {
        TagItem tag = tag("EX_SELF_ORDER", EvidenceStatus.VERIFIED, "[피해자]가 [받은계좌1]로 300만원을 직접 이체했다", "[받은계좌1]", "300만원");
        Result result = service.judge(List.of(item("EX_SELF_ORDER")), tagged ? List.of(tag) : List.of(),
                answer == null ? Map.of() : Map.of("EX_SELF_ORDER", answer),
                List.of(tx(1, "482901-01-336417", "3000000", access)), LABELS, List.of());
        return result.lines().getFirst();
    }

    @Test
    void 기록이_UNKNOWN이면_직접_이체_근거가_있어도_확인_필요다() {
        Line line = judgeSelfOrder(UNKNOWN, true, null);

        assertThat(line.status()).isEqualTo(NEEDS_CHECK);
        assertThat(line.access()).isEqualTo(UNKNOWN);
    }

    @Test
    void 기록이_없고_직접_이체_근거가_있으면_해당이다() {
        assertThat(judgeSelfOrder(NONE, true, null).status()).isEqualTo(APPLIES);
        assertThat(judgeSelfOrder(NONE, false, null).status()).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 원격_기록이_있고_직접_이체_근거가_없으면_비해당이고_NO_답은_답과_다름이_아니다() {
        Line line = judgeSelfOrder(REMOTE_OR_NEW_DEVICE, false, "NO");

        assertThat(line.status()).isEqualTo(NOT_APPLIES);
        assertThat(line.answerMismatch()).isFalse();
    }

    @Test
    void 원격_기록이_있고_직접_이체_근거가_답_YES뿐이면_확인_필요이고_답과_다름이다() {
        Line line = judgeSelfOrder(REMOTE_OR_NEW_DEVICE, false, "YES");

        assertThat(line.status()).isEqualTo(NEEDS_CHECK);
        assertThat(line.answerMismatch()).isTrue();
    }

    @Test
    void 담당자_항목은_답이_없으면_확인_필요이고_해당_답이면_해당이다() {
        assertThat(judgeOne("EX_LAWSUIT", List.of(), Map.of()).status()).isEqualTo(NEEDS_CHECK);

        Line yes = judgeOne("EX_LAWSUIT", List.of(), Map.of("EX_LAWSUIT", "YES"));
        assertThat(yes.status()).isEqualTo(APPLIES);
        assertThat(yes.answerMismatch()).isFalse();
        assertThat(judgeOne("EX_LAWSUIT", List.of(), Map.of("EX_LAWSUIT", "NO")).status()).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 채널_BRANCH와_ATM_거래는_EX_FACE_해당이고_MOBILE_APP_ATM은_비해당이다() {
        assertThat(dataStatus("EX_FACE", dataTx(TransactionChannel.BRANCH, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(APPLIES);
        assertThat(dataStatus("EX_FACE", dataTx(TransactionChannel.ATM, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(APPLIES);
        assertThat(dataStatus("EX_FACE", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(NOT_APPLIES);
        assertThat(dataStatus("EX_FACE", dataTx(TransactionChannel.APP_ATM, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(NOT_APPLIES);
    }

    @Test
    void 채널을_모르면_EX_FACE_EX_CARD_확인_필요다() {
        CaseTx unknown = dataTx(null, CounterpartyType.BANK, WarningRecord.NONE);

        assertThat(dataStatus("EX_FACE", unknown)).isEqualTo(NEEDS_CHECK);
        assertThat(dataStatus("EX_CARD", unknown)).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 채널_CARD는_EX_CARD_해당이고_다른_채널은_비해당이다() {
        assertThat(dataStatus("EX_CARD", dataTx(TransactionChannel.CARD, null, WarningRecord.NONE))).isEqualTo(APPLIES);
        assertThat(dataStatus("EX_CARD", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(NOT_APPLIES);
    }

    @Test
    void 받는_곳이_EFIN이면_EX_EFIN_해당이고_BANK는_비해당이고_모르면_확인_필요다() {
        assertThat(dataStatus("EX_EFIN", dataTx(TransactionChannel.MOBILE, CounterpartyType.EFIN, WarningRecord.NONE))).isEqualTo(APPLIES);
        assertThat(dataStatus("EX_EFIN", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(NOT_APPLIES);
        assertThat(dataStatus("EX_EFIN", dataTx(TransactionChannel.MOBILE, null, WarningRecord.NONE))).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 경고에_정상_거래라고_답했으면_EX_WARNED_해당이고_경고_없음은_비해당이고_모르면_확인_필요다() {
        assertThat(dataStatus("EX_WARNED", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.CLAIMED_NORMAL))).isEqualTo(APPLIES);
        assertThat(dataStatus("EX_WARNED", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.NONE))).isEqualTo(NOT_APPLIES);
        assertThat(dataStatus("EX_WARNED", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, WarningRecord.UNKNOWN))).isEqualTo(NEEDS_CHECK);
        assertThat(dataStatus("EX_WARNED", dataTx(TransactionChannel.MOBILE, CounterpartyType.BANK, null))).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 다른_사건_접수일이_첫_피해_거래보다_앞서면_EX_PRIOR_RELIEF_해당이고_뒤면_비해당이고_목록이_null이면_확인_필요다() {
        List<CaseTx> txs = List.of(tx(1, "a", "1", NONE), tx(2, "b", "1", NONE));

        assertThat(priorStatus(List.of(T0.minusDays(30)), txs)).isEqualTo(APPLIES);
        assertThat(priorStatus(List.of(T0.plusDays(1)), txs)).isEqualTo(NOT_APPLIES);
        assertThat(priorStatus(List.of(T0), txs)).isEqualTo(NOT_APPLIES);
        assertThat(priorStatus(List.of(), txs)).isEqualTo(NOT_APPLIES);
        assertThat(priorStatus(null, txs)).isEqualTo(NEEDS_CHECK);
        assertThat(priorStatus(List.of(T0.minusDays(30)), List.of())).isEqualTo(NEEDS_CHECK);
    }

    @Test
    void 데이터_항목은_신청서_답이_있어도_답과_다름이_없고_판정도_바뀌지_않는다() {
        CaseTx card = dataTx(TransactionChannel.CARD, null, WarningRecord.NONE);
        for (String answer : List.of("YES", "NO")) {
            Line line = service.judge(List.of(item("EX_CARD")), List.of(), Map.of("EX_CARD", answer), List.of(card), LABELS, List.of())
                    .lines().getFirst();

            assertThat(line.status()).isEqualTo(APPLIES);
            assertThat(line.answerMismatch()).isFalse();
            assertThat(line.tags()).isEmpty();
        }
    }

    @Test
    void 규칙이_없는_데이터_항목은_범위와_상관없이_예외다() {
        for (CheckItem.Scope scope : CheckItem.Scope.values()) {
            CheckItem unknown = new CheckItem("EX_NEW", item("EX_CARD").kind(), "새 항목", scope, CheckItem.JudgeBy.DATA, java.util.Set.of());
            assertThatThrownBy(() -> service.judge(List.of(unknown), List.of(), Map.of(), List.of(tx(1, "a", "1", NONE)), LABELS, List.of()))
                    .as(scope.name()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EX_NEW");
        }
    }

    @Test
    void 거래에_못_잇는_거래_단위_태그는_잇지_못한_태그로_나오고_어느_거래의_근거도_아니다() {
        TagItem noLabel = tag("EX_SELF_ORDER", EvidenceStatus.VERIFIED, "안전계좌로 직접 이체했다", "직접 이체했고");
        TagItem wrongAmount = tag("EX_SELF_ORDER", EvidenceStatus.VERIFIED, "[받은계좌1]로 700만원을 직접 이체했다", "[받은계좌1]", "700만원");
        List<CaseTx> txs = List.of(tx(1, "482901-01-336417", "3000000", NONE), tx(2, "110-472-836150", "9500000", REMOTE_OR_NEW_DEVICE));

        Result result = service.judge(List.of(item("EX_SELF_ORDER")), List.of(noLabel, wrongAmount), Map.of(), txs, LABELS, List.of());

        assertThat(result.unlinkedTags()).containsExactly(noLabel, wrongAmount);
        assertThat(result.lines()).hasSize(2).allMatch(l -> l.tags().isEmpty());
    }

    private static List<CheckItem> nonData() {
        return COMP_ITEMS.stream().filter(i -> i.judgeBy() != CheckItem.JudgeBy.DATA).toList();
    }

    private static List<CaseTx> kimTxs() {
        return List.of(tx(1, "482901-01-336417", "3000000", NONE), tx(2, "110-472-836150", "9500000", REMOTE_OR_NEW_DEVICE));
    }

    private static Map<String, String> kimAnswers() throws IOException {
        Map<String, String> answers = new HashMap<>();
        JsonMapper.shared().readTree(TestFiles.kimWoori("08_자율배상신청서.json")).get("answers")
                .properties().forEach(e -> answers.put(e.getKey(), e.getValue().asString()));
        return answers;
    }
}
