package com.withwon.integration.ai.dto;

/**
 * LLM 호출 한 번(ai_call_log 한 행). input은 보낸 가린 글이고, 지시문은 promptVersion으로 안다(직원-10).
 * model은 실제로 답한 모델이고, 거절돼 답이 없으면 model과 output이 null이다.
 */
public record LlmCall(String provider, String model, String promptVersion, String input, String output) {
}
