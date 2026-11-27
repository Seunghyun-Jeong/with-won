package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.withwon.integration.ai.dto.DocCheck;
import com.withwon.integration.ai.dto.DocCheckResult;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.MaskLabel;
import java.util.List;
import org.junit.jupiter.api.Test;

// 서류 점검 요약(직원-24, finding DOC_CHECK). 근거 대조와 이름표 잇기는 요약, 태깅과 같은 LlmTask.Run.check다.
class DocCheckServiceTest {

    // 가린 뒤의 글이다. 6번은 가림에 실패해 masked_text가 null인 조각.
    private static final List<EvidenceFragment> FRAGMENTS = List.of(
            new EvidenceFragment(1, "피해구제신청서", null, null, "성 명 [피해자]"),
            new EvidenceFragment(2, "피해구제신청서", null, null, "피해 발생일 2026. 11. 18."),
            new EvidenceFragment(3, "사건사고사실확인원", null, null, "성명 [인물1]"),
            new EvidenceFragment(4, "사건사고사실확인원", null, null, "피해일시 2026. 11. 16."),
            new EvidenceFragment(5, "문자캡처", null, null, "[검찰청] 사건 조회 링크"),
            new EvidenceFragment(6, "문자캡처", null, null, null));

    private static final List<MaskLabel> LABELS = List.of(
            new MaskLabel("[피해자]", MaskLabel.Kind.PERSON, "김우리"),
            new MaskLabel("[인물1]", MaskLabel.Kind.PERSON, "김유리"));

    private static final String DATE = issue("피해구제신청서", "신청서의 피해 발생일과 확인원의 피해일시 날짜가 다르다",
            ev(2, "2026. 11. 18."), ev(4, "2026. 11. 16."));

    private static String answer(String... issues) {
        return "{\"issues\": [" + String.join(",", issues) + "]}";
    }

    private static String issue(String document, String text, String... evidence) {
        return "{\"document\": \"" + document + "\", \"text\": \"" + text + "\", \"evidence\": [" + String.join(",", evidence) + "]}";
    }

    private static String ev(long fragmentId, String quote) {
        return "{\"fragmentId\": " + fragmentId + ", \"quote\": \"" + quote + "\"}";
    }

    private DocCheckResult run(String... issues) {
        DocCheckResult result = new DocCheckService(new FakeLlm(answer(issues))).check(FRAGMENTS, LABELS);
        assertThat(result.failReason()).isNull();
        return result;
    }

    @Test
    void 문제를_대상_서류에_붙이고_문제가_없는_서류도_입력_순서대로_남긴다() {
        DocCheckResult result = run(issue("사건사고사실확인원", "확인원의 피해자 이름 [인물1]이 신청서의 [피해자]와 다르다",
                ev(3, "성명 [인물1]"), ev(1, "성 명 [피해자]")), DATE);

        assertThat(result.documents()).extracting(DocCheck::document, DocCheck::needsSupplement).containsExactly(
                tuple("피해구제신청서", true), tuple("사건사고사실확인원", true), tuple("문자캡처", false));
        FindingText name = result.documents().get(1).issues().getFirst();
        assertThat(name.status()).isEqualTo(EvidenceStatus.VERIFIED);
        assertThat(name.displayText()).isEqualTo("확인원의 피해자 이름 김유리이 신청서의 김우리와 다르다");
        assertThat(result.documents().getFirst().issues()).extracting(FindingText::text)
                .containsExactly("신청서의 피해 발생일과 확인원의 피해일시 날짜가 다르다");
        assertThat(result.documents().getLast().issues()).isEmpty();
    }

    @Test
    void 근거가_틀린_문제만_근거_부족이고_그것만으로는_보완_필요가_아니다() {
        DocCheckResult result = run(DATE, issue("문자캡처", "문자 발신 번호가 잘려 있다", ev(5, "발신 010-12")));

        assertThat(result.documents().getFirst().issues().getFirst().status()).isEqualTo(EvidenceStatus.VERIFIED);
        DocCheck sms = result.documents().getLast();
        assertThat(sms.issues()).extracting(FindingText::status).containsExactly(EvidenceStatus.NO_EVIDENCE);
        assertThat(sms.needsSupplement()).isFalse();
    }

    @Test
    void 근거에_대상_서류의_조각이_없으면_확인_필요다() {
        DocCheckResult result = run(issue("문자캡처", "피해 발생일이 다르다", ev(2, "2026. 11. 18."), ev(4, "2026. 11. 16.")));

        FindingText wrongTarget = result.documents().getLast().issues().getFirst();
        assertThat(wrongTarget.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
        assertThat(result.documents().getLast().needsSupplement()).isTrue();
    }

    @Test
    void 입력에_없는_서류_이름은_형식_오류로_다시_묻고_세_번_틀리면_실패한다() {
        String bad = answer(issue("신분증", "사진이 흐리다", ev(1, "성 명")));
        FakeLlm llm = new FakeLlm(bad, bad, bad);

        DocCheckResult result = new DocCheckService(llm).check(FRAGMENTS, LABELS);

        assertThat(result.documents()).isNull();
        assertThat(result.failReason()).startsWith("형식 오류").contains("issues[0].document");
        assertThat(result.calls()).hasSize(3)
                .allSatisfy(c -> assertThat(c.promptVersion()).isEqualTo(DocCheckService.PROMPT_VERSION));
        assertThat(llm.schemas.getFirst()).contains("\"enum\": [\"피해구제신청서\", \"사건사고사실확인원\", \"문자캡처\"]");
    }

    @Test
    void 형식_오류를_고쳐_답하면_그_답을_쓴다() {
        FakeLlm llm = new FakeLlm("{\"issues\": [{\"document\": \"피해구제신청서\"}]}", answer(DATE));

        DocCheckResult result = new DocCheckService(llm).check(FRAGMENTS, LABELS);

        assertThat(result.failReason()).isNull();
        assertThat(result.calls()).hasSize(2);
        assertThat(llm.inputs.getLast()).contains("## 지난 답의 형식 오류", "issues[0].text");
        assertThat(result.documents().getFirst().needsSupplement()).isTrue();
    }

    @Test
    void 거절하면_다시_묻지_않고_실패한다() {
        DocCheckResult result = new DocCheckService(new FakeLlm(FakeLlm.REFUSE)).check(FRAGMENTS, LABELS);

        assertThat(result.failReason()).startsWith("LLM 거절");
        assertThat(result.calls()).hasSize(1);
    }
}
