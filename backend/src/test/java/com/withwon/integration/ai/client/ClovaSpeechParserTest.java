package com.withwon.integration.ai.client;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.dto.Utterance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// 응답 예시는 NAVER Cloud 공식 문서 "로컬 파일 인식"의 sync JSON 응답 예시다.
// 문서가 가린 _ncp_TaskId(**442)만 JSON이 되도록 442로 바꿨다.
class ClovaSpeechParserTest {

    private final ClovaSpeechParser parser = new ClovaSpeechParser(new JsonMapper());

    @Test
    void segment마다_화자_이름과_시작_끝_시각과_글을_가진_발화가_된다() throws IOException {
        assertThat(parser.parse(TestFiles.officialSpeechExample())).containsExactly(
                new Utterance("B", 5870, 8160, "서울 수영장입니다."),
                new Utterance("A", 8160, 12950, "입장료가 얼마예요? 5천 원이에요. 감사합니다."));
    }

    @Test
    void 인식에_실패하면_예외를_던진다() throws IOException {
        String failed = TestFiles.officialSpeechExample().replace("\"result\": \"COMPLETED\"", "\"result\": \"ERROR_AUDIO_CONVERSION\"");

        assertThatThrownBy(() -> parser.parse(failed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERROR_AUDIO_CONVERSION");
    }
}
