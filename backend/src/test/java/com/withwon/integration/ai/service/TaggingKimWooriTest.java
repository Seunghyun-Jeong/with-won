package com.withwon.integration.ai.service;

import static com.withwon.integration.ai.TestFiles.TAG_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.TagItem;
import com.withwon.integration.ai.dto.TagResult;
import java.io.IOException;
import org.junit.jupiter.api.Test;

// 김우리 서류와 앱에서 쓴 자율배상 신청서를 실제 흐름대로 가린 글(KimWooriCase)로, 행위 태그의 근거가 가린 글과 맞고 이름표가 실제 값으로 이어지는지 본다.
class TaggingKimWooriTest {

    private final KimWooriCase kim = new KimWooriCase();

    @Test
    void 김우리_원격제어_앱_설치와_주민번호_뒷자리와_OTP를_불러_준_일과_직접_이체가_근거와_함께_태깅된다() throws IOException {
        kim.add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("사건사고사실확인원", "02_사건사고사실확인원.pdf.txt")
                .add("문자캡처", "05_문자캡처.png.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt")
                .addCompForm();
        long app = kim.idContaining("사건사고사실확인원", "원격제어 기능이 있는 악성 앱을 설치하게");
        long rrn = kim.idContaining("자율배상신청서", "주민등록번호 뒷자리를 물어 불러 주었습니다");
        long transfer = kim.idContaining("자율배상신청서", "300만원을 직접 이체했고, OTP 번호도 불러 주었습니다");
        long account = kim.idContaining("자율배상신청서", "안전계좌라며 KB국민은행 [받은계좌1]");
        String answer = """
                {"tags": [
                  {"itemCode": "EX_SELF_ORDER", "text": "[피해자]가 [받은계좌1]로 300만원을 직접 이체했다",
                   "evidence": [{"fragmentId": %1$d, "quote": "KB국민은행 [받은계좌1]"}, {"fragmentId": %2$d, "quote": "300만원을 직접 이체했고"}]},
                  {"itemCode": "FT_GAVE_SECURITY", "text": "[피해자]가 통화 중 OTP 번호를 불러 주었다",
                   "evidence": [{"fragmentId": %2$d, "quote": "OTP 번호도 불러 주었습니다"}]},
                  {"itemCode": "FT_GAVE_PERSONAL", "text": "[피해자]가 본인 확인이라는 말에 주민등록번호 뒷자리를 불러 주었다",
                   "evidence": [{"fragmentId": %3$d, "quote": "주민등록번호 뒷자리를 물어 불러 주었습니다"}]},
                  {"itemCode": "FT_MALICIOUS_APP", "text": "[피해자]가 원격제어 기능이 있는 악성 앱을 설치했다",
                   "evidence": [{"fragmentId": %4$d, "quote": "원격제어 기능이 있는 악성 앱을 설치하게"}]}]}"""
                .formatted(account, transfer, rrn, app);
        FakeLlm llm = new FakeLlm(answer);

        TagResult result = new TaggingService(llm).tag(TAG_ITEMS, kim.fragments, kim.labels);

        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "482901", "010-2847");
        assertThat(result.tags()).extracting(TagItem::itemCode)
                .containsExactly("FT_MALICIOUS_APP", "FT_GAVE_PERSONAL", "FT_GAVE_SECURITY", "EX_SELF_ORDER");
        assertThat(result.tags()).allMatch(t -> t.finding().status() == EvidenceStatus.VERIFIED);
        assertThat(result.tags().getLast().finding().displayText()).isEqualTo("김우리가 482901-01-336417로 300만원을 직접 이체했다");
        assertThat(result.notFound()).contains("FT_NO_LOCK", "FT_SMISHING_LINK").doesNotContain("FT_GAVE_SECURITY");
    }
}
