package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskLabel.Kind;
import com.withwon.integration.ai.dto.SummaryItem;
import com.withwon.integration.ai.dto.SummaryResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 사건 요약 작업(직원-16). 근거 대조와 이름표 잇기는 타임라인과 같은 규칙이고, 문장 속 금액 대조가 더해진다.
class SummaryServiceTest {

    // 가린 뒤의 글이다. 6번은 가림에 실패해 masked_text가 null인 조각.
    private static final List<EvidenceFragment> FRAGMENTS = List.of(
            new EvidenceFragment(1, "피해구제신청서", null, null,
                    "2026년 11월 18일 오후 1시 50분경 서울중앙지방검찰청 수사관이라는 사람에게서 전화를 받았습니다."),
            new EvidenceFragment(2, "피해구제신청서", null, null, "오후 2시 32분에 [피해자] 계좌에서 300만원을 [받은계좌1]로 이체했고"),
            new EvidenceFragment(3, "이체내역캡처", null, null, "-3,000,000원"),
            new EvidenceFragment(4, "이체내역캡처", null, null, "-9,500,000원"),
            new EvidenceFragment(5, "이체내역캡처", null, null, "[예금주1]"),
            new EvidenceFragment(6, "이체내역캡처", null, null, null));

    private static final List<MaskLabel> LABELS = List.of(
            new MaskLabel("[피해자]", Kind.PERSON, "김우리"),
            new MaskLabel("[받은계좌1]", Kind.ACCOUNT, "482901-01-336417"),
            new MaskLabel("[예금주1]", Kind.PERSON, "박민수"));

    private static final String CALL = item("검찰 수사관을 사칭한 전화로 사건이 시작됐다",
            ev(1, "서울중앙지방검찰청 수사관이라는 사람에게서 전화를 받았습니다"));

    private static String answer(List<String> summary, List<String> checks) {
        return "{\"summary\": [" + String.join(",", summary) + "], \"checks\": [" + String.join(",", checks) + "]}";
    }

    private static String item(String text, String... evidence) {
        return "{\"text\": \"" + text + "\", \"evidence\": [" + String.join(",", evidence) + "]}";
    }

    private static String ev(long fragmentId, String quote) {
        return "{\"fragmentId\": " + fragmentId + ", \"quote\": \"" + quote + "\"}";
    }

    private SummaryResult run(FakeLlm llm) {
        return new SummaryService(llm).summarize(FRAGMENTS, LABELS);
    }

    private SummaryResult run(List<String> summary, List<String> checks) {
        SummaryResult result = run(new FakeLlm(answer(summary, checks)));
        assertThat(result.failReason()).isNull();
        return result;
    }

    @Test
    void 입력은_타임라인과_같은_서류별_줄_글이고_가림_실패_조각은_보내지_않는다() {
        FakeLlm llm = new FakeLlm(answer(List.of(CALL), List.of()));

        run(llm);

        assertThat(llm.inputs.getFirst()).contains("## 서류: 이체내역캡처\n#3 -3,000,000원\n#4 -9,500,000원\n#5 [예금주1]\n")
                .doesNotContain("#6");
    }

    @Test
    void 요약_문장과_확인_필요_항목을_근거와_함께_받고_이름표를_실제_값으로_잇는다() {
        SummaryResult result = run(
                List.of(CALL, item("[피해자]가 [받은계좌1]로 300만원을 이체했다", ev(2, "300만원을 [받은계좌1]로 이체했고"))),
                List.of(item("이체내역의 [예금주1] 계좌가 신청서의 [받은계좌1]과 같은지 확인", ev(5, "[예금주1]"),
                        ev(2, "[받은계좌1]"))));

        assertThat(result.sentences()).extracting(SummaryItem::displayText).containsExactly(
                "검찰 수사관을 사칭한 전화로 사건이 시작됐다", "김우리가 482901-01-336417로 300만원을 이체했다");
        assertThat(result.sentences().getLast().text()).isEqualTo("[피해자]가 [받은계좌1]로 300만원을 이체했다");
        assertThat(result.checks()).singleElement().satisfies(c -> {
            assertThat(c.displayText()).isEqualTo("이체내역의 박민수 계좌가 신청서의 482901-01-336417과 같은지 확인");
            assertThat(c.status()).isEqualTo(EvidenceStatus.VERIFIED);
        });
        assertThat(result.sentences()).allMatch(s -> s.status() == EvidenceStatus.VERIFIED
                && s.unknownLabels().isEmpty() && s.unverifiedAmounts().isEmpty());
        assertThat(result.calls()).singleElement()
                .satisfies(c -> assertThat(c.promptVersion()).isEqualTo(SummaryService.PROMPT_VERSION));
    }

    @Test
    void 근거가_틀린_문장만_근거_부족이고_나머지_문장은_그대로_남는다() {
        SummaryResult result = run(List.of(CALL, item("[피해자]가 앱을 설치했다", ev(6, "앱을 설치"))), List.of());

        assertThat(result.sentences()).extracting(SummaryItem::status)
                .containsExactly(EvidenceStatus.VERIFIED, EvidenceStatus.NO_EVIDENCE);
        assertThat(result.sentences().getLast().evidence()).singleElement()
                .satisfies(e -> assertThat(e.fragmentId()).isNull());
    }

    @Test
    void 대응표로_잇지_못한_이름표가_있으면_확인_필요다() {
        SummaryItem s = run(List.of(item("[받은계좌2]로도 이체했다", ev(2, "[받은계좌1]로 이체했고"))), List.of())
                .sentences().getFirst();

        assertThat(s.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(s.unknownLabels()).containsExactly("[받은계좌2]");
    }

    @Test
    void 문장의_금액이_확인된_근거에_없으면_확인_필요로_내리고_그_금액을_남긴다() {
        SummaryItem s = run(List.of(item("[피해자]가 두 번에 걸쳐 모두 12,500,000원을 보냈다",
                ev(3, "-3,000,000원"), ev(4, "-9,500,000원"))), List.of()).sentences().getFirst();

        assertThat(s.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(s.unverifiedAmounts()).containsExactly("12,500,000원");
    }

    @Test
    void 금액은_단위를_맞춰_대조한다() {
        SummaryItem s = run(List.of(item("[피해자]가 300만원과 950만원을 보냈다",
                ev(3, "-3,000,000원"), ev(4, "-9,500,000원"))), List.of()).sentences().getFirst();

        assertThat(s.status()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(s.unverifiedAmounts()).isEmpty();
    }

    @Test
    void 금액은_확인된_근거에서만_찾는다() {
        SummaryItem s = run(List.of(item("950만원 이체가 수사관 전화 뒤에 있었다",
                ev(1, "수사관이라는 사람에게서"), ev(4, "9,500,000원 이체"))), List.of()).sentences().getFirst();

        assertThat(s.unverifiedAmounts()).containsExactly("950만원");
    }

    @Test
    void 확인_필요_항목도_같은_금액_규칙으로_검증한다() {
        SummaryItem c = run(List.of(CALL), List.of(item("이체 합계 1,250만원을 원장과 대조해야 함",
                ev(3, "-3,000,000원"), ev(4, "-9,500,000원")))).checks().getFirst();

        assertThat(c.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(c.unverifiedAmounts()).containsExactly("1,250만원");
    }

    @Test
    void 요약_문장이_하나도_없으면_형식_오류로_다시_묻는다() {
        FakeLlm llm = new FakeLlm(answer(List.of(), List.of()), answer(List.of(CALL), List.of()));

        SummaryResult result = run(llm);

        assertThat(result.failReason()).isNull();
        assertThat(result.sentences()).hasSize(1);
        assertThat(result.calls()).hasSize(2);
        assertThat(llm.inputs.get(1)).contains("형식 오류");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"summary\": [{\"text\": \"요약\", \"evidence\": []}]}",
            "{\"summary\": [{\"evidence\": []}], \"checks\": []}",
            "{\"summary\": [{\"text\": \"요약\", \"evidence\": [{\"fragmentId\": 1}]}], \"checks\": []}",
            "{\"summary\": [{\"text\": \"요약\", \"evidence\": []}], \"checks\": [{\"text\": \" \", \"evidence\": []}]}"})
    void JSON_모양이_틀리면_세_번_묻고_실패다(String wrong) {
        SummaryResult result = run(new FakeLlm(wrong, wrong, wrong));

        assertThat(result.sentences()).isNull();
        assertThat(result.checks()).isNull();
        assertThat(result.failReason()).startsWith("형식 오류");
        assertThat(result.calls()).hasSize(3);
    }

    @Test
    void 답_JSON_스키마를_함께_넘겨_API가_모양을_강제하게_한다() {
        FakeLlm llm = new FakeLlm(answer(List.of(CALL), List.of()));

        run(llm);

        JsonNode schema = JsonMapper.builder().build().readTree(llm.schemas.getFirst());
        assertThat(schema.path("required").toString()).isEqualTo("[\"summary\",\"checks\"]");
        for (String field : List.of("summary", "checks")) {
            JsonNode item = schema.at("/properties/" + field + "/items");
            assertThat(item.path("required").toString()).isEqualTo("[\"text\",\"evidence\"]");
            assertThat(item.at("/properties/evidence/items/properties/fragmentId/type").stringValue()).isEqualTo("integer");
        }
        assertThat(schema.findValues("additionalProperties")).hasSize(5).allMatch(v -> v.isBoolean() && !v.booleanValue());
    }
}
