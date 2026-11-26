package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.SummaryItem;
import com.withwon.integration.ai.dto.SummaryResult;
import java.io.IOException;
import org.junit.jupiter.api.Test;

// 김우리 서류를 실제 흐름대로 가린 글(KimWooriCase)로 요약의 금액 대조를 본다. 가림이 금액을 온전히 남겨야 대조가 된다.
class SummaryKimWooriTest {

    private final KimWooriCase kim = new KimWooriCase();

    @Test
    void 김우리_서류의_금액을_단위가_달라도_대조하고_근거에_없는_합계는_확인_필요로_내린다() throws IOException {
        kim.add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("사건사고사실확인원", "02_사건사고사실확인원.pdf.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt");

        // 이체내역은 이정호, 박민수 행이 [예금주n] / -금액 순서로 읽혔다(원장 시각순으로 박민수가 1, 이정호가 2).
        long holder1 = kim.idOf("이체내역캡처", "[예금주1]", 0);
        long holder2 = kim.idOf("이체내역캡처", "[예금주2]", 0);
        long applicant = kim.idContaining("피해구제신청서", "[피해자]");
        long total = kim.idContaining("사건사고사실확인원", "12,500,000원");
        String answer = """
                {"summary": [
                  {"text": "[피해자]가 [예금주1]에게 300만원, [예금주2]에게 950만원을 보냈다",
                   "evidence": [{"fragmentId": %d, "quote": "[피해자]"}, {"fragmentId": %d, "quote": "[예금주1]"},
                                {"fragmentId": %d, "quote": "-3,000,000원"}, {"fragmentId": %d, "quote": "[예금주2]"},
                                {"fragmentId": %d, "quote": "-9,500,000원"}]},
                  {"text": "경찰 확인원의 피해액은 1,250만원이다", "evidence": [{"fragmentId": %d, "quote": "12,500,000원"}]}],
                 "checks": [
                  {"text": "이체 합계 1,250만원을 원장과 대조해야 함",
                   "evidence": [{"fragmentId": %d, "quote": "-3,000,000원"}, {"fragmentId": %d, "quote": "-9,500,000원"}]}]}"""
                .formatted(applicant, holder1, holder1 + 1, holder2, holder2 + 1, total, holder1 + 1, holder2 + 1);
        FakeLlm llm = new FakeLlm(answer);

        SummaryResult result = new SummaryService(llm).summarize(kim.fragments, kim.labels);

        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "이정호", "482901", "110-472");
        assertThat(result.sentences()).allMatch(s -> s.status() == EvidenceStatus.VERIFIED);
        assertThat(result.sentences()).extracting(SummaryItem::displayText).containsExactly(
                "김우리가 박민수에게 300만원, 이정호에게 950만원을 보냈다", "경찰 확인원의 피해액은 1,250만원이다");
        assertThat(result.checks()).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(EvidenceStatus.NEEDS_CHECK);
            assertThat(c.unverifiedAmounts()).containsExactly("1,250만원");
        });
    }
}
