package com.withwon.integration.ai.client;

import com.withwon.integration.ai.dto.LlmReply;

/**
 * 외부 LLM 한 번 부르기. Claude, GPT, 내부 LLM을 이 뒤에서 바꿔 끼운다.
 * 받는 글은 이미 가린 글이어야 한다(MaskService.maskForLlm). 이 인터페이스는 가리지 않는다.
 */
public interface LlmClient {

    /** ai_call_log.provider. Claude, GPT 등 */
    String provider();

    /**
     * system은 작업 지시문, user는 가린 근거 글, jsonSchema는 답 JSON의 모양(JSON Schema 글).
     * 모양을 API가 강제할 수 있는 LLM은 강제하고, 못 하는 LLM은 지시로만 쓴다. 어느 쪽이든 작업 서비스가 다시 검사한다.
     * 안전 분류기가 거절하면 LlmRefusedException을 던진다.
     */
    LlmReply complete(String system, String user, String jsonSchema);
}
