package com.withwon.integration.ai.service;

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
    void 김우리_링크와_원격제어_앱_설치와_주민번호_뒷자리와_OTP를_불러_준_일과_직접_이체가_근거와_함께_태깅된다() throws IOException {
        TagResult result = kim.tagRecorded();
        FakeLlm llm = kim.llm;

        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "482901", "010-2847");
        assertThat(result.tags()).extracting(TagItem::itemCode)
                .containsExactly("FT_SMISHING_LINK", "FT_MALICIOUS_APP", "FT_GAVE_PERSONAL", "FT_GAVE_SECURITY", "EX_SELF_ORDER");
        assertThat(result.tags()).allMatch(t -> t.finding().status() == EvidenceStatus.VERIFIED);
        assertThat(result.tags().getLast().finding().displayText()).isEqualTo("김우리가 482901-01-336417로 300만원을 직접 이체했다");
        assertThat(result.notFound()).contains("FT_NO_LOCK").doesNotContain("FT_SMISHING_LINK", "FT_GAVE_SECURITY");
    }
}
