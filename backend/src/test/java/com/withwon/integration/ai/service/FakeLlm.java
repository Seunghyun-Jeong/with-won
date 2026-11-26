package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.client.LlmRefusedException;
import com.withwon.integration.ai.dto.LlmReply;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 진짜 LLM 대신 정해 둔 답을 차례로 돌려주고 받은 글과 스키마를 남긴다. 답이 REFUSE면 거절한다. LLM 작업 시험이 같이 쓴다. */
final class FakeLlm implements LlmClient {

    static final String REFUSE = "REFUSE";
    private final Deque<String> answers;
    final List<String> inputs = new ArrayList<>();
    final List<String> schemas = new ArrayList<>();

    FakeLlm(String... answers) {
        this.answers = new ArrayDeque<>(List.of(answers));
    }

    @Override
    public String provider() {
        return "FAKE";
    }

    @Override
    public LlmReply complete(String system, String user, String jsonSchema) {
        inputs.add(user);
        schemas.add(jsonSchema);
        String answer = answers.removeFirst();
        if (answer.equals(REFUSE)) {
            throw new LlmRefusedException("cyber");
        }
        return new LlmReply(answer, "fake-1");
    }
}
