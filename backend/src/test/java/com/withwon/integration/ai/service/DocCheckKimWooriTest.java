package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.withwon.integration.ai.dto.DocCheck;
import com.withwon.integration.ai.dto.DocCheckResult;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import java.io.IOException;
import org.junit.jupiter.api.Test;

// 김우리 필수 서류와 증빙을 실제 흐름대로 가린 글(KimWooriCase)로, 서류 점검 문제가 실제 조각 근거로 대상 서류에 붙고 지어낸 문제는 걸러지는지 본다.
// 김우리 서류에는 일부러 넣은 불일치가 없고, 확인원 OCR에 뜻을 알 수 없는 줄이 하나 있다.
class DocCheckKimWooriTest {

    private final KimWooriCase kim = new KimWooriCase();

    @Test
    void 김우리_확인원의_깨진_글은_근거와_함께_붙고_지어낸_서명_누락은_근거_부족이다() throws IOException {
        kim.add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("사건사고사실확인원", "02_사건사고사실확인원.pdf.txt")
                .add("문자캡처", "05_문자캡처.png.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt")
                .addCompForm();
        long garbled = kim.idContaining("사건사고사실확인원", "식제 문서 공동회사 채출동");
        String answer = """
                {"issues": [
                  {"document": "사건사고사실확인원", "text": "뜻을 알 수 없는 글이 한 줄 있다",
                   "evidence": [{"fragmentId": %d, "quote": "식제 문서 공동회사 채출동"}]},
                  {"document": "피해구제신청서", "text": "신청인 서명이 없다",
                   "evidence": [{"fragmentId": 1, "quote": "서명 없음"}]}]}""".formatted(garbled);
        FakeLlm llm = new FakeLlm(answer);

        DocCheckResult result = new DocCheckService(llm).check(kim.fragments, kim.labels);

        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "900512-2123456", "010-2847");
        assertThat(result.documents()).extracting(DocCheck::document, DocCheck::needsSupplement).containsExactly(
                tuple("피해구제신청서", false), tuple("사건사고사실확인원", true), tuple("문자캡처", false),
                tuple("이체내역캡처", false), tuple("자율배상신청서", false));
        assertThat(result.documents().get(1).issues()).extracting(FindingText::status).containsExactly(EvidenceStatus.VERIFIED);
        assertThat(result.documents().getFirst().issues()).extracting(FindingText::status)
                .containsExactly(EvidenceStatus.NO_EVIDENCE);
    }
}
