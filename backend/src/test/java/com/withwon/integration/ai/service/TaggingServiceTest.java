package com.withwon.integration.ai.service;

import static com.withwon.integration.ai.TestFiles.TAG_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.withwon.integration.ai.dto.CheckItem;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TagItem;
import com.withwon.integration.ai.dto.TagResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 자율배상 태깅(직원-35 제외 사유 신호, 직원-36 과실 행위). 근거 대조, 이름표, 금액 규칙은 요약과 같은 LlmTask.Run.check다.
class TaggingServiceTest {

    // 가린 뒤의 글이다. 6번은 가림에 실패해 masked_text가 null인 조각.
    private static final List<EvidenceFragment> FRAGMENTS = List.of(
            new EvidenceFragment(1, "피해구제신청서", null, null, "문자로 받은 링크를 눌러 보안점검 앱을 설치했습니다."),
            new EvidenceFragment(2, "피해구제신청서", null, null, "본인 확인이라며 주민등록번호 뒷자리를 물어 불러 주었습니다."),
            new EvidenceFragment(3, "피해구제신청서", null, null,
                    "[피해자] 계좌에서 300만원을 [받은계좌1]로 직접 이체했고 OTP 번호도 불러 주었습니다."),
            new EvidenceFragment(4, "이체내역캡처", null, null, "-3,000,000원"),
            new EvidenceFragment(5, "이체내역캡처", null, null, "-9,500,000원"),
            new EvidenceFragment(6, "이체내역캡처", null, null, null));

    private static final List<MaskLabel> LABELS = List.of(
            new MaskLabel("[피해자]", MaskLabel.Kind.PERSON, "김우리"),
            new MaskLabel("[받은계좌1]", MaskLabel.Kind.ACCOUNT, "482901-01-336417"));

    private static final String APP = tag("FT_MALICIOUS_APP", "[피해자]가 문자 링크에서 보안점검 앱을 설치했다",
            ev(1, "보안점검 앱을 설치했습니다"));

    private static String answer(String... tags) {
        return "{\"tags\": [" + String.join(",", tags) + "]}";
    }

    private static String tag(String code, String text, String... evidence) {
        return "{\"itemCode\": \"" + code + "\", \"text\": \"" + text + "\", \"evidence\": [" + String.join(",", evidence) + "]}";
    }

    private static String ev(long fragmentId, String quote) {
        return "{\"fragmentId\": " + fragmentId + ", \"quote\": \"" + quote + "\"}";
    }

    private static List<String> codesExcept(String... found) {
        return TAG_ITEMS.stream().map(CheckItem::code).filter(c -> !List.of(found).contains(c)).toList();
    }

    private TagResult run(FakeLlm llm) {
        return new TaggingService(llm).tag(TAG_ITEMS, FRAGMENTS, LABELS);
    }

    private TagResult run(String... tags) {
        TagResult result = run(new FakeLlm(answer(tags)));
        assertThat(result.failReason()).isNull();
        return result;
    }

    @Test
    void 입력_앞에_점검_항목을_코드와_이름으로_붙이고_서류별_줄_글을_잇는다() {
        FakeLlm llm = new FakeLlm(answer(APP));

        run(llm);

        String input = llm.inputs.getFirst();
        assertThat(input).startsWith("## 점검 항목\n- FT_ID_LOST_UNREPORTED: 신분증을 잃어버리고 바로 분실신고를 하지 않음\n")
                .contains("- EX_SELF_ORDER: 피해자가 직접 이체를 지시함")
                .contains("## 서류: 이체내역캡처\n#4 -3,000,000원\n#5 -9,500,000원\n")
                .doesNotContain("#6");
        assertThat(input.indexOf("- EX_SELF_ORDER")).isLessThan(input.indexOf("## 서류:"));
    }

    @Test
    void 찾은_행위를_근거와_함께_받고_이름표를_잇고_점검_항목의_종류를_붙인다() {
        TagResult result = run(
                tag("EX_SELF_ORDER", "[피해자]가 [받은계좌1]로 300만원을 직접 이체했다", ev(3, "300만원을 [받은계좌1]로 직접 이체했고")),
                tag("FT_GAVE_SECURITY", "[피해자]가 OTP 번호를 불러 주었다", ev(3, "OTP 번호도 불러 주었습니다")));

        assertThat(result.tags()).extracting(TagItem::itemCode, TagItem::kind).containsExactly(
                tuple("FT_GAVE_SECURITY", CheckItem.Kind.FAULT),
                tuple("EX_SELF_ORDER", CheckItem.Kind.EXCLUSION));
        FindingText self = result.tags().getLast().finding();
        assertThat(self.text()).isEqualTo("[피해자]가 [받은계좌1]로 300만원을 직접 이체했다");
        assertThat(self.displayText()).isEqualTo("김우리가 482901-01-336417로 300만원을 직접 이체했다");
        assertThat(result.tags()).allMatch(t -> t.finding().status() == EvidenceStatus.VERIFIED);
        assertThat(result.calls()).singleElement()
                .satisfies(c -> assertThat(c.promptVersion()).isEqualTo(TaggingService.PROMPT_VERSION));
    }

    @Test
    void 같은_항목이_여러_번_오면_각각_남기고_점검_항목_순서로_놓는다() {
        TagResult result = run(
                tag("EX_SELF_ORDER", "[피해자]가 300만원을 직접 이체했다", ev(3, "300만원을 [받은계좌1]로 직접 이체했고")),
                APP,
                tag("EX_SELF_ORDER", "[피해자]가 950만원을 직접 이체했다", ev(5, "-9,500,000원")));

        assertThat(result.tags()).extracting(t -> t.finding().text()).containsExactly(
                "[피해자]가 문자 링크에서 보안점검 앱을 설치했다",
                "[피해자]가 300만원을 직접 이체했다",
                "[피해자]가 950만원을 직접 이체했다");
    }

    @Test
    void 근거가_틀린_태그만_근거_부족이고_나머지_태그는_그대로_남는다() {
        TagResult result = run(APP, tag("FT_GAVE_PERSONAL", "[피해자]가 주민등록번호 뒷자리를 불러 주었다", ev(6, "뒷자리")));

        assertThat(result.tags()).extracting(t -> t.finding().status())
                .containsExactly(EvidenceStatus.VERIFIED, EvidenceStatus.NO_EVIDENCE);
        assertThat(result.tags().getLast().finding().evidence()).singleElement()
                .satisfies(e -> assertThat(e.fragmentId()).isNull());
    }

    @Test
    void 확인된_근거가_있는_태그가_없는_항목은_찾지_못한_항목으로_점검_항목_순서대로_남는다() {
        TagResult result = run(APP, tag("FT_GAVE_PERSONAL", "[피해자]가 주민등록번호 앞자리를 불러 주었다", ev(2, "주민등록번호 앞자리")));

        assertThat(result.notFound()).isEqualTo(codesExcept("FT_MALICIOUS_APP")).contains("FT_GAVE_PERSONAL");
    }

    @Test
    void 태그가_하나도_없어도_성공이고_모든_항목이_찾지_못한_항목이다() {
        TagResult result = run();

        assertThat(result.tags()).isEmpty();
        assertThat(result.notFound()).isEqualTo(codesExcept());
        assertThat(result.calls()).hasSize(1);
    }

    @Test
    void 태그_문장의_금액이_확인된_근거에_없으면_확인_필요로_내린다() {
        FindingText f = run(tag("EX_SELF_ORDER", "[피해자]가 모두 1,250만원을 직접 이체했다",
                ev(4, "-3,000,000원"), ev(5, "-9,500,000원"))).tags().getFirst().finding();

        assertThat(f.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(f.unverifiedAmounts()).containsExactly("1,250만원");
    }

    @Test
    void 넘긴_점검_항목에_없는_코드는_형식_오류로_다시_묻는다() {
        FakeLlm llm = new FakeLlm(answer(tag("EX_LAWSUIT", "은행과 소송 중이다", ev(1, "앱을 설치"))), answer(APP));

        TagResult result = run(llm);

        assertThat(result.failReason()).isNull();
        assertThat(result.tags()).extracting(TagItem::itemCode).containsExactly("FT_MALICIOUS_APP");
        assertThat(result.calls()).hasSize(2);
        assertThat(llm.inputs.get(1)).contains("형식 오류", "EX_LAWSUIT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"tags\": [{\"itemCode\": \"FT_NO_LOCK\", \"evidence\": []}]}",
            "{\"tags\": [{\"itemCode\": \"EX_LAWSUIT\", \"text\": \"소송 중\", \"evidence\": []}]}",
            "{\"tags\": [{\"itemCode\": 4, \"text\": \"잠금 없음\", \"evidence\": []}]}",
            "{\"tags\": [{\"itemCode\": \"FT_NO_LOCK\", \"text\": \"잠금 없음\", \"evidence\": [{\"quote\": \"잠금\"}]}]}"})
    void JSON_모양이_틀리면_세_번_묻고_실패다(String wrong) {
        TagResult result = run(new FakeLlm(wrong, wrong, wrong));

        assertThat(result.tags()).isNull();
        assertThat(result.notFound()).isNull();
        assertThat(result.failReason()).startsWith("형식 오류");
        assertThat(result.calls()).hasSize(3);
    }

    @Test
    void 답_JSON_스키마의_항목_코드를_넘긴_점검_항목으로_묶어_API가_강제하게_한다() {
        FakeLlm llm = new FakeLlm(answer(APP));

        run(llm);

        JsonNode schema = JsonMapper.builder().build().readTree(llm.schemas.getFirst());
        assertThat(schema.path("required").toString()).isEqualTo("[\"tags\"]");
        JsonNode tag = schema.at("/properties/tags/items");
        assertThat(tag.path("required").toString()).isEqualTo("[\"itemCode\",\"text\",\"evidence\"]");
        assertThat(tag.at("/properties/itemCode/enum").valueStream().map(JsonNode::stringValue).toList())
                .isEqualTo(codesExcept());
        assertThat(tag.at("/properties/evidence/items/properties/fragmentId/type").stringValue()).isEqualTo("integer");
        assertThat(schema.findValues("additionalProperties")).hasSize(3).allMatch(v -> v.isBoolean() && !v.booleanValue());
    }
}
