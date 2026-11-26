package com.withwon.integration.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.withwon.integration.ai.dto.LlmReply;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude Messages API(공식 Java SDK)로 LLM을 부른다.
 *
 * <p>모델은 Claude Sonnet 5.5다(11-26 선택, 100만 토큰당 입력 $2, 출력 $10. Opus 5.5의 절반). effort는 이 모델의 기본값 high를 둔다.
 * 답 모양은 structured outputs(output_config.format)로 API가 강제한다. 작업 서비스의 형식 검사는 강제가 없는 다른 LLM을 위한 안전망이다.
 * 안전 분류기가 거절하면 서버가 거절 분류에 맞는 다른 모델로 넘겨 답하게 한다(fallbacks default, 베타).
 * 보이스피싱 대화라 사기 수법 글이 거절에 걸릴 수 있어서다. 그래서 답한 모델을 LlmReply에 따로 담는다.
 * 시간 초과, 429, 5xx는 SDK가 2번 다시 시도하고, 그 뒤 실패는 예외 그대로 작업 큐(직원-4)에 넘긴다.
 */
public class ClaudeLlmClient implements LlmClient {

    static final String MODEL = "claude-sonnet-5-5";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    // 타임라인 답은 수천 토큰으로 본다. 모자라면 잘린 JSON이 형식 오류로 잡힌다.
    private static final long MAX_TOKENS = 16_000L;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private final AnthropicClient client;

    public ClaudeLlmClient(String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Override
    public String provider() {
        return "Claude";
    }

    @Override
    public LlmReply complete(String system, String user, String jsonSchema) {
        BetaJsonOutputFormat.Schema.Builder schema = BetaJsonOutputFormat.Schema.builder();
        JSON.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {})
                .forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(system)
                .addUserMessage(user)
                .outputConfig(BetaOutputConfig.builder()
                        .format(BetaJsonOutputFormat.builder().schema(schema.build()).build()).build())
                .addBeta(FALLBACK_BETA)
                .fallbacksDefault()
                .build();
        BetaMessage message = client.beta().messages().create(params);
        log.info("Claude {} 입력 {} 출력 {} 토큰, 끝 {}", message.model().asString(), message.usage().inputTokens(),
                message.usage().outputTokens(), message.stopReason().map(Object::toString).orElse("?"));
        return reply(message);
    }

    static LlmReply reply(BetaMessage message) {
        if (message.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            throw new LlmRefusedException(message.stopDetails().flatMap(d -> d.category())
                    .map(c -> c.asString()).orElse("unknown"));
        }
        String text = message.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text())
                .collect(Collectors.joining());
        return new LlmReply(text, message.model().asString());
    }
}
