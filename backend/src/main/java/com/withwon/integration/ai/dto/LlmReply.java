package com.withwon.integration.ai.dto;

/** LLM 답 한 번. model은 실제로 답한 모델이다(거절 뒤 대체 모델이 답할 수 있어 부른 모델과 다를 수 있음, ai_call_log.model). */
public record LlmReply(String text, String model) {
}
