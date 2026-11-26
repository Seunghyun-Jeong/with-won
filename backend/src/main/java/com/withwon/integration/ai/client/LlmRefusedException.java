package com.withwon.integration.ai.client;

/** LLM의 안전 분류기가 답을 거절함. 같은 글로 다시 물어도 같으므로 형식 오류처럼 다시 묻지 않는다. 메시지는 거절 분류(cyber 등)다. */
public class LlmRefusedException extends RuntimeException {
    public LlmRefusedException(String category) {
        super(category);
    }
}
