package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskLabel.Kind;
import com.withwon.integration.ai.dto.TimelineEvent;
import com.withwon.integration.ai.dto.TimelineResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 타임라인 작업. 진짜 LLM 대신 정해 둔 답을 돌려주는 FakeLlm으로 코드의 검증과 잇기만 본다.
class TimelineServiceTest {

    // 가린 뒤의 글이다. 9번은 가림에 실패해 masked_text가 null인 조각.
    private static final List<EvidenceFragment> FRAGMENTS = List.of(
            new EvidenceFragment(1, "문자캡처", null, null, "2026년 11월 18일 (수)"),
            new EvidenceFragment(2, "문자캡처", null, null, "[서울중앙지검] [피해자]님 사건 관련 보안점검 앱을 설치하십시오."),
            new EvidenceFragment(3, "문자캡처", null, null, "오후 1:56"),
            new EvidenceFragment(4, "이체내역캡처", null, null, "11.18"),
            new EvidenceFragment(5, "이체내역캡처", null, null, "14:32"),
            new EvidenceFragment(6, "이체내역캡처", null, null, "[예금주1]"),
            new EvidenceFragment(7, "이체내역캡처", null, null, "-3,000,000원"),
            new EvidenceFragment(8, "통화녹음1", "A", 65_000, "저는 서울중앙지방검찰청 [인물1] 수사관입니다."),
            new EvidenceFragment(9, "통화녹음1", "B", 70_000, null));

    private static final List<MaskLabel> LABELS = List.of(
            new MaskLabel("[피해자]", Kind.PERSON, "김우리"),
            new MaskLabel("[받은계좌1]", Kind.ACCOUNT, "482901-01-336417"),
            new MaskLabel("[예금주1]", Kind.PERSON, "박민수"),
            new MaskLabel("[인물1]", Kind.PERSON, "김민준"));

    private static final String TRANSFER = """
            {"what": "[피해자]가 [예금주1]에게 3,000,000원을 이체함", "dateText": "11.18", "timeText": "14:32",
             "evidence": [{"fragmentId": 4, "quote": "11.18"}, {"fragmentId": 5, "quote": "14:32"},
                          {"fragmentId": 6, "quote": "[예금주1]"}, {"fragmentId": 7, "quote": "-3,000,000원"}]}""";

    private static String answer(String... events) {
        return "{\"events\": [" + String.join(",", events) + "]}";
    }

    private static String event(String what, String evidence) {
        return "{\"what\": \"" + what + "\", \"evidence\": [" + evidence + "]}";
    }

    private TimelineResult run(FakeLlm llm) {
        return new TimelineService(llm).build(FRAGMENTS, LABELS, 2026);
    }

    private TimelineEvent only(String... events) {
        TimelineResult result = run(new FakeLlm(answer(events)));
        assertThat(result.failReason()).isNull();
        return result.events().getFirst();
    }

    @Test
    void 입력은_서류별로_묶고_조각_ID를_붙이며_가림_실패_조각은_보내지_않는다() {
        FakeLlm llm = new FakeLlm(answer());

        run(llm);

        String input = llm.inputs.getFirst();
        assertThat(input).contains("## 서류: 문자캡처\n#1 2026년 11월 18일 (수)\n#2 ")
                .contains("## 서류: 통화녹음1\n#8 A 01:05 저는 서울중앙지방검찰청 [인물1] 수사관입니다.")
                .doesNotContain("#9");
    }

    @Test
    void 근거가_모두_맞으면_확인됨이고_이름표는_실제_값으로_잇고_시각은_정규화한다() {
        TimelineEvent e = only(TRANSFER);

        assertThat(e.status()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(e.what()).isEqualTo("[피해자]가 [예금주1]에게 3,000,000원을 이체함");
        assertThat(e.displayWhat()).isEqualTo("김우리가 박민수에게 3,000,000원을 이체함");
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(e.time()).isEqualTo(LocalTime.of(14, 32));
        assertThat(e.evidence()).allMatch(Evidence::verified);
        assertThat(e.unknownLabels()).isEmpty();
    }

    @Test
    void 근거_ID가_보낸_조각에_없으면_그_근거는_ID를_비우고_근거_부족이다() {
        TimelineEvent e = only(event("[인물1]이 전화함", "{\"fragmentId\": 99, \"quote\": \"수사관입니다\"}"));

        assertThat(e.evidence()).containsExactly(new Evidence(null, "수사관입니다", false));
        assertThat(e.status()).isEqualTo(EvidenceStatus.NO_EVIDENCE);
    }

    @Test
    void 가림에_실패해_보내지_않은_조각의_ID도_없는_ID로_본다() {
        TimelineEvent e = only(event("[피해자]가 답함", "{\"fragmentId\": 9, \"quote\": \"네\"}"));

        assertThat(e.evidence()).containsExactly(new Evidence(null, "네", false));
        assertThat(e.status()).isEqualTo(EvidenceStatus.NO_EVIDENCE);
    }

    @Test
    void 근거_일부만_맞으면_확인_필요다() {
        TimelineEvent e = only(event("[인물1]이 검사를 사칭함",
                "{\"fragmentId\": 8, \"quote\": \"서울중앙지방검찰청\"}, {\"fragmentId\": 99, \"quote\": \"검사\"}"));

        assertThat(e.evidence()).extracting(Evidence::verified).containsExactly(true, false);
        assertThat(e.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
    }

    @Test
    void 인용문이_가린_글에_없으면_확인되지_않는다() {
        // 실제 값(박민수)으로 인용하면 LLM이 받지 않은 글이다.
        TimelineEvent e = only(event("[예금주1]에게 이체함", "{\"fragmentId\": 6, \"quote\": \"박민수\"}"));

        assertThat(e.evidence()).containsExactly(new Evidence(6L, "박민수", false));
        assertThat(e.status()).isEqualTo(EvidenceStatus.NO_EVIDENCE);
    }

    @Test
    void 인용문은_공백_차이를_무시하고_대조한다() {
        TimelineEvent e = only(event("앱 설치 문자", "{\"fragmentId\": 3, \"quote\": \"오후  1:56 \"}"));

        assertThat(e.status()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    void 대응표에_없는_이름표는_잇지_않고_확인_필요로_내린다() {
        TimelineEvent e = only(event("[인물7]이 [피해자]에게 전화함", "{\"fragmentId\": 8, \"quote\": \"수사관입니다\"}"));

        assertThat(e.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(e.unknownLabels()).containsExactly("[인물7]");
        assertThat(e.displayWhat()).isEqualTo("[인물7]이 김우리에게 전화함");
    }

    @Test
    void 대응표에_있어도_보낸_글에_없던_이름표는_믿지_않는다() {
        TimelineEvent e = only(event("[받은계좌1]로 이체함", "{\"fragmentId\": 7, \"quote\": \"-3,000,000원\"}"));

        assertThat(e.unknownLabels()).containsExactly("[받은계좌1]");
        assertThat(e.displayWhat()).isEqualTo("[받은계좌1]로 이체함");
        assertThat(e.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
    }

    @Test
    void 이름표가_아닌_원문의_대괄호는_그대로_둔다() {
        TimelineEvent e = only(event("[서울중앙지검] 사칭 문자로 앱 설치를 요구함",
                "{\"fragmentId\": 2, \"quote\": \"보안점검 앱을 설치하십시오\"}"));

        assertThat(e.unknownLabels()).isEmpty();
        assertThat(e.status()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    void 시각_표현이_확인된_근거에_없으면_시각을_비운다() {
        TimelineEvent e = only("""
                {"what": "[인물1]이 전화함", "dateText": "11월 17일", "timeText": "오후 3시",
                 "evidence": [{"fragmentId": 8, "quote": "수사관입니다"}]}""");

        assertThat(e.date()).isNull();
        assertThat(e.time()).isNull();
        assertThat(e.status()).isEqualTo(EvidenceStatus.VERIFIED);
    }

    @Test
    void 날짜와_시각은_서로_다른_조각에서_올_수_있다() {
        TimelineEvent e = only("""
                {"what": "앱 설치 문자를 받음", "dateText": "2026년 11월 18일", "timeText": "오후 1:56",
                 "evidence": [{"fragmentId": 1, "quote": "2026년 11월 18일"}, {"fragmentId": 2, "quote": "보안점검 앱"},
                              {"fragmentId": 3, "quote": "오후 1:56"}]}""");

        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(e.time()).isEqualTo(LocalTime.of(13, 56));
    }

    private static final String CALL = event("[인물1]이 전화함", "{\"fragmentId\": 8, \"quote\": \"수사관입니다\"}");
    private static final String SMS = """
            {"what": "앱 설치 문자를 받음", "dateText": "2026년 11월 18일", "timeText": "오후 1:56",
             "evidence": [{"fragmentId": 1, "quote": "2026년 11월 18일"}, {"fragmentId": 3, "quote": "오후 1:56"}]}""";

    private List<String> order(String... events) {
        return run(new FakeLlm(answer(events))).events().stream().map(TimelineEvent::what).toList();
    }

    @Test
    void 아는_시각끼리는_LLM이_순서를_틀려도_코드가_바로잡는다() {
        assertThat(order(TRANSFER, SMS))
                .containsExactly("앱 설치 문자를 받음", "[피해자]가 [예금주1]에게 3,000,000원을 이체함");
    }

    @Test
    void 시각을_모르는_항목은_LLM_순서상_바로_앞_항목_자리에_머문다() {
        // 전화는 이체 바로 뒤에 왔으므로 이체 시각을 빌려 이체 뒤에 남는다. 맨 뒤로 밀리지 않는다.
        assertThat(order(TRANSFER, CALL, SMS)).containsExactly(
                "앱 설치 문자를 받음", "[피해자]가 [예금주1]에게 3,000,000원을 이체함", "[인물1]이 전화함");
        assertThat(order(CALL, SMS, TRANSFER)).containsExactly(
                "[인물1]이 전화함", "앱 설치 문자를 받음", "[피해자]가 [예금주1]에게 3,000,000원을 이체함");
    }

    @Test
    void 빌린_시각은_정렬에만_쓰고_항목에는_시각_불명으로_남긴다() {
        TimelineEvent call = run(new FakeLlm(answer(SMS, CALL))).events().getLast();

        assertThat(call.date()).isNull();
        assertThat(call.time()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "타임라인입니다",
            "{\"items\": []}",
            "{\"events\": [{\"evidence\": []}]}",
            "{\"events\": [{\"what\": \"  \", \"evidence\": []}]}",
            "{\"events\": [{\"what\": \"전화\", \"evidence\": [{\"fragmentId\": \"8\", \"quote\": \"수사관\"}]}]}",
            "{\"events\": [{\"what\": \"전화\", \"evidence\": [{\"fragmentId\": 8}]}]}",
            "{\"events\": [{\"what\": \"전화\", \"timeText\": 14, \"evidence\": []}]}"})
    void JSON_모양이_틀리면_틀린_점을_붙여_다시_묻고_세_번_틀리면_실패다(String wrong) {
        FakeLlm llm = new FakeLlm(wrong, wrong, wrong);

        TimelineResult result = run(llm);

        assertThat(result.events()).isNull();
        assertThat(result.failReason()).isNotBlank();
        assertThat(result.calls()).hasSize(3);
        assertThat(llm.inputs.get(1)).startsWith(llm.inputs.getFirst()).contains("형식 오류");
    }

    @Test
    void 다시_물어_맞는_답이_오면_그_답을_쓰고_호출을_모두_남긴다() {
        TimelineResult result = run(new FakeLlm("```json 깨진 답", answer(TRANSFER)));

        assertThat(result.failReason()).isNull();
        assertThat(result.events()).hasSize(1);
        assertThat(result.calls()).hasSize(2).allSatisfy(c -> {
            assertThat(c.provider()).isEqualTo("FAKE");
            assertThat(c.model()).isEqualTo("fake-1");
            assertThat(c.promptVersion()).isEqualTo(TimelineService.PROMPT_VERSION);
        });
        assertThat(result.calls().getFirst().output()).isEqualTo("```json 깨진 답");
    }

    @Test
    void 빈_타임라인도_맞는_답이다() {
        TimelineResult result = run(new FakeLlm(answer()));

        assertThat(result.events()).isEmpty();
        assertThat(result.calls()).hasSize(1);
    }

    @Test
    void 답_JSON_스키마를_함께_넘겨_API가_모양을_강제하게_한다() {
        FakeLlm llm = new FakeLlm(answer());

        run(llm);

        JsonNode schema = JsonMapper.builder().build().readTree(llm.schemas.getFirst());
        assertThat(schema.path("required").toString()).isEqualTo("[\"events\"]");
        JsonNode event = schema.at("/properties/events/items");
        assertThat(event.path("required").toString()).isEqualTo("[\"what\",\"dateText\",\"timeText\",\"evidence\"]");
        assertThat(event.at("/properties/evidence/items/properties/fragmentId/type").stringValue()).isEqualTo("integer");
        assertThat(schema.findValues("additionalProperties")).hasSize(3).allMatch(v -> v.isBoolean() && !v.booleanValue());
    }

    @Test
    void LLM이_거절하면_다시_묻지_않고_거절로_실패한다() {
        TimelineResult result = run(new FakeLlm(FakeLlm.REFUSE, answer()));

        assertThat(result.events()).isNull();
        assertThat(result.failReason()).isEqualTo("LLM 거절: cyber");
        assertThat(result.calls()).singleElement().satisfies(c -> assertThat(c.output()).isNull());
    }
}
