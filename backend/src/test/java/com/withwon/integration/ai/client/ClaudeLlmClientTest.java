package com.withwon.integration.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.withwon.integration.ai.dto.LlmReply;
import org.junit.jupiter.api.Test;

// Claude 응답을 우리 답으로 바꾸는 부분. 응답은 Messages API가 돌려주는 JSON 모양 그대로 SDK로 읽는다(네트워크 없음).
class ClaudeLlmClientTest {

    private static BetaMessage message(String json) throws Exception {
        return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
    }

    @Test
    void 글_블록을_이어_붙이고_실제로_답한_모델을_돌려준다() throws Exception {
        BetaMessage m = message("""
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-sonnet-5",
                 "content": [{"type": "thinking", "thinking": "", "signature": "x"},
                             {"type": "text", "text": "{\\"events\\": "}, {"type": "text", "text": "[]}"}],
                 "stop_reason": "end_turn", "stop_sequence": null,
                 "usage": {"input_tokens": 10, "output_tokens": 5}}""");

        assertThat(ClaudeLlmClient.reply(m)).isEqualTo(new LlmReply("{\"events\": []}", "claude-sonnet-5"));
    }

    @Test
    void 거절이면_거절_분류를_담아_알린다() throws Exception {
        BetaMessage m = message("""
                {"id": "msg_2", "type": "message", "role": "assistant", "model": "claude-sonnet-5-5",
                 "content": [], "stop_reason": "refusal", "stop_sequence": null,
                 "stop_details": {"type": "refusal", "category": "cyber", "explanation": null},
                 "usage": {"input_tokens": 10, "output_tokens": 0}}""");

        assertThatThrownBy(() -> ClaudeLlmClient.reply(m)).isInstanceOf(LlmRefusedException.class)
                .hasMessage("cyber");
    }
}
