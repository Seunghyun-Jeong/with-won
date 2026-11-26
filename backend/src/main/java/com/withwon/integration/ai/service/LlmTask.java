package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.client.LlmRefusedException;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.LlmCall;
import com.withwon.integration.ai.dto.LlmReply;
import com.withwon.integration.ai.dto.MaskLabel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가린 근거 조각으로 LLM 작업 하나를 부르는 공용 흐름이다. 타임라인, 요약, 태깅이 같이 쓰고 작업마다 지시문, 답 모양, 모양 검사만 다르다.
 *
 * <p>입력은 서류별로 묶은 줄 글이다. 녹음은 발화가 수백 개라 조각마다 JSON 객체로 보내면 같은 키가 반복돼 토큰이 몇 배로 는다.
 * 조각 ID는 fragment.id 그대로 "#숫자"로 붙여 이름표의 대괄호와 헷갈리지 않게 했다. 가림에 실패한 조각(maskedText null)은 보내지 않는다.
 *
 * <p>실패는 두 층이다. 답 전체의 JSON 모양이 틀리면 틀린 점을 붙여 {@value #FORMAT_RETRIES}번까지 다시 묻고, 그래도 틀리면
 * 실패로 돌려준다(직원-8, ai_job.fail_kind=FORMAT). 모양은 맞는데 항목의 근거나 이름표가 틀리면 작업이 그 항목만 표시하고 남긴다
 * (직원-9 "근거 없음으로 표시"). LLM이 거절하면 같은 글로 다시 물어도 같으므로 바로 실패로 돌려준다.
 * 시간 초과 같은 일시적 실패의 재시도는 작업 큐(직원-4)가 맡는다.
 *
 * <p>답 모양은 schema로 LLM에 같이 넘겨 Claude처럼 API가 강제할 수 있으면 강제하게 한다. 지시문에도 같은 모양을 적어 두는 것은
 * 강제가 없는 LLM을 위해서다(structured outputs는 모든 칸이 required여야 해서 없을 수 있는 칸은 null을 허용한다).
 */
final class LlmTask {

    // 직원-8 제안값. 형식 오류는 같은 글로 다시 물어도 고쳐지는 경우가 많지만 세 번째에도 틀리면 지시문 문제로 본다.
    static final int FORMAT_RETRIES = 2;

    /** 작업 지시문에 같이 넣는 읽기 규칙. 입력 모양(input), 이름표 잇기(LabelLinker), 근거 대조(EvidenceVerifier)와 짝이라 함께 고친다. */
    static final String READING_RULES = """
            - 조각은 서류별로 묶여 있고 각 줄 앞의 #숫자가 조각 ID다. 녹음은 "#ID 화자 분:초 글" 모양이다.
            - [피해자], [받은계좌1], [인물1], [지역1] 같은 대괄호는 사람, 번호, 주소를 바꾼 이름표다. 이름표를 그대로 써라. 없는 이름표를 만들지 마라.
            - 누가 사기범인지는 문맥으로 판단해 글로 써라.
            - 근거는 조각 ID와, 그 조각 글에서 그대로 옮긴 짧은 인용문으로 대라. 글자를 고치지 마라.
            """;

    /** 답 항목의 evidence 칸 모양. 근거 대조가 읽는 칸(fragmentId, quote)과 짝이다. */
    static final String EVIDENCE_SCHEMA = """
            {"type": "array", "items": {
                   "type": "object", "additionalProperties": false, "required": ["fragmentId", "quote"],
                   "properties": {"fragmentId": {"type": "integer"}, "quote": {"type": "string"}}}}""";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 작업 한 번의 결과. answer는 모양 검사를 통과한 답이고, 실패하면 null이고 failReason이 있다.
     * verifier와 linker는 이번에 실제로 보낸 가린 글과 대응표로 만든 것이라 답의 항목을 대조할 때 쓴다.
     */
    record Run(JsonNode answer, String failReason, List<LlmCall> calls, EvidenceVerifier verifier, LabelLinker linker) {

        /** 답 항목 하나의 evidence 칸을 보낸 글과 대조한다 */
        List<Evidence> evidence(JsonNode item) {
            return item.path("evidence").valueStream()
                    .map(ev -> verifier.verify(ev.path("fragmentId").longValue(), ev.path("quote").stringValue()))
                    .toList();
        }

        /**
         * 답 항목 하나의 글 칸(textField)과 근거를 대조해 finding 한 건으로 만든다. 요약과 태깅이 같이 쓴다.
         * 잇지 못한 이름표나 근거에 없는 금액이 있으면 근거가 모두 맞아도 확인 필요다.
         */
        FindingText check(JsonNode item, String textField) {
            String text = item.path(textField).stringValue();
            List<Evidence> evidence = evidence(item);
            List<String> unknown = linker.unknown(text);
            List<String> amounts = verifier.unverifiedAmounts(text, evidence);
            EvidenceStatus status = EvidenceVerifier.status(evidence, !unknown.isEmpty() || !amounts.isEmpty());
            return new FindingText(text, linker.display(text), status, evidence, unknown, amounts);
        }
    }

    private final LlmClient llm;
    private final String system;
    private final String schema;
    private final String promptVersion;
    private final Function<JsonNode, List<String>> shapeErrors;

    /** shapeErrors: 답 JSON에서 작업이 요구하는 모양과 다른 점들. 없으면 빈 목록 */
    LlmTask(LlmClient llm, String system, String schema, String promptVersion,
            Function<JsonNode, List<String>> shapeErrors) {
        this.llm = llm;
        this.system = system;
        this.schema = schema;
        this.promptVersion = promptVersion;
        this.shapeErrors = shapeErrors;
    }

    Run run(List<EvidenceFragment> fragments, List<MaskLabel> labels) {
        return run("", fragments, labels);
    }

    /** preface는 서류 앞에 붙는 작업 입력이다(태깅의 점검 항목 목록처럼 호출마다 다를 수 있어 지시문이 아니라 호출 기록에 남아야 하는 것). */
    Run run(String preface, List<EvidenceFragment> fragments, List<MaskLabel> labels) {
        List<EvidenceFragment> sent = fragments.stream().filter(f -> f.maskedText() != null).toList();
        Map<Long, String> texts = sent.stream().collect(Collectors.toMap(EvidenceFragment::id, EvidenceFragment::maskedText));
        EvidenceVerifier verifier = new EvidenceVerifier(texts);
        LabelLinker linker = new LabelLinker(labels, texts.values());
        String input = preface + input(sent);
        List<LlmCall> calls = new ArrayList<>();
        String user = input;
        List<String> errors = List.of();
        for (int attempt = 0; attempt <= FORMAT_RETRIES; attempt++) {
            LlmReply reply;
            try {
                reply = llm.complete(system, user, schema);
            } catch (LlmRefusedException e) {
                calls.add(new LlmCall(llm.provider(), null, promptVersion, user, null));
                return new Run(null, "LLM 거절: " + e.getMessage(), calls, verifier, linker);
            }
            String output = reply.text();
            calls.add(new LlmCall(llm.provider(), reply.model(), promptVersion, user, output));
            JsonNode root = parse(output);
            errors = root == null ? List.of("JSON으로 읽을 수 없음") : shapeErrors.apply(root);
            if (errors.isEmpty()) {
                return new Run(root, null, calls, verifier, linker);
            }
            user = input + "\n\n## 지난 답의 형식 오류\n- " + String.join("\n- ", errors)
                    + "\n지시한 JSON 모양으로만 다시 답하라.";
        }
        return new Run(null, "형식 오류: " + String.join(", ", errors), calls, verifier, linker);
    }

    /** 답 항목 하나의 글 칸(textField)과 evidence 칸 모양을 보고 틀린 점을 errors에 더한다. at은 오류에 적을 위치다. */
    static void itemErrors(JsonNode item, String textField, String at, List<String> errors) {
        JsonNode text = item.path(textField);
        if (!text.isString() || text.stringValue().isBlank()) {
            errors.add(at + "." + textField + "은 빈 칸이 아닌 문자열이어야 함");
        }
        if (!item.path("evidence").isArray()) {
            errors.add(at + ".evidence 배열이 없음");
            return;
        }
        List<JsonNode> evidence = item.path("evidence").valueStream().toList();
        for (int j = 0; j < evidence.size(); j++) {
            JsonNode ev = evidence.get(j);
            if (!ev.path("fragmentId").isIntegralNumber()) {
                errors.add(at + ".evidence[" + j + "].fragmentId는 정수여야 함");
            }
            if (!ev.path("quote").isString() || ev.path("quote").stringValue().isBlank()) {
                errors.add(at + ".evidence[" + j + "].quote는 빈 칸이 아닌 문자열이어야 함");
            }
        }
    }

    private static String input(List<EvidenceFragment> sent) {
        Map<String, List<EvidenceFragment>> byDocument = sent.stream()
                .collect(Collectors.groupingBy(EvidenceFragment::document, LinkedHashMap::new, Collectors.toList()));
        StringBuilder sb = new StringBuilder();
        byDocument.forEach((document, list) -> {
            sb.append("## 서류: ").append(document).append('\n');
            for (EvidenceFragment f : list) {
                sb.append('#').append(f.id()).append(' ');
                if (f.speaker() != null) {
                    sb.append(f.speaker()).append(' ');
                }
                if (f.startMs() != null) {
                    int seconds = f.startMs() / 1000;
                    sb.append("%02d:%02d ".formatted(seconds / 60, seconds % 60));
                }
                sb.append(f.maskedText()).append('\n');
            }
        });
        return sb.toString();
    }

    private static JsonNode parse(String output) {
        try {
            return JSON.readTree(output);
        } catch (JacksonException e) {
            return null;
        }
    }
}
