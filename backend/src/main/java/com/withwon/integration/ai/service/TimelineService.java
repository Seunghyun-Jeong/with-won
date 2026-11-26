package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.client.LlmRefusedException;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.LlmCall;
import com.withwon.integration.ai.dto.LlmReply;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TimelineEvent;
import com.withwon.integration.ai.dto.TimelineResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가린 근거 조각으로 사건 타임라인을 만든다(직원-6, 직원-9, 11-26).
 * LLM은 문맥을 읽어 사건 항목을 고르고 근거 조각 ID와 인용문, 근거에 적힌 날짜와 시각 표현을 옮긴다.
 * 누가 사기범인지도 LLM이 문맥으로 본다. 코드는 근거 대조(EvidenceVerifier), 이름표 잇기(LabelLinker),
 * 시각 정규화(TimeNormalizer)와 정렬을 하고, 확정은 담당자가 한다(직원-19).
 *
 * <p>입력은 서류별로 묶은 줄 글이다. 녹음은 발화가 수백 개라 조각마다 JSON 객체로 보내면 같은 키가 반복돼 토큰이 몇 배로 는다.
 * 조각 ID는 fragment.id 그대로 "#숫자"로 붙여 이름표의 대괄호와 헷갈리지 않게 했다.
 *
 * <p>실패는 두 층이다. 답 전체의 JSON 모양이 틀리면 틀린 점을 붙여 {@value #FORMAT_RETRIES}번까지 다시 묻고, 그래도 틀리면
 * 실패로 돌려준다(직원-8, ai_job.fail_kind=FORMAT). 모양은 맞는데 항목의 근거나 이름표가 틀리면 그 항목만 표시하고 남긴다
 * (직원-9 "근거 없음으로 표시"). LLM이 거절하면 같은 글로 다시 물어도 같으므로 바로 실패로 돌려준다.
 * 시간 초과 같은 일시적 실패의 재시도는 작업 큐(직원-4)가 맡는다.
 *
 * <p>답 모양은 SCHEMA로 LLM에 같이 넘겨 Claude처럼 API가 강제할 수 있으면 강제하게 한다. 지시문에도 같은 모양을 적어 두는 것은
 * 강제가 없는 LLM을 위해서다. 날짜, 시각 표현은 없을 수 있어 null을 허용하되 칸은 늘 두게 했다(structured outputs는 모든 칸이 required여야 함).
 *
 * <p>경위 글은 첫 문장에만 날짜를 쓰고 뒤 문장은 시각만 쓰는 일이 많다(김우리 자율배상 신청서에서 11개 중 4개 항목이 날짜 없음, 11-26).
 * 앞 문장의 날짜를 쓰게 하되 코드가 채우지 않고 LLM이 날짜가 적힌 조각을 근거로 대게 했다(timeline-v3). 그래야 다른 날의 날짜가
 * 잘못 넘어오면 근거 대조에서 걸리고, 담당자가 날짜의 출처를 눌러 볼 수 있다.
 */
public class TimelineService {

    public static final String PROMPT_VERSION = "timeline-v3";
    // 직원-8 제안값. 형식 오류는 같은 글로 다시 물어도 고쳐지는 경우가 많지만 세 번째에도 틀리면 지시문 문제로 본다.
    static final int FORMAT_RETRIES = 2;

    private static final String SYSTEM = """
            너는 보이스피싱 피해구제 심사를 돕는 정리 담당이다. 아래 근거 조각을 읽고 사건이 일어난 순서대로 사건 항목을 뽑아라.
            - 조각은 서류별로 묶여 있고 각 줄 앞의 #숫자가 조각 ID다. 녹음은 "#ID 화자 분:초 글" 모양이다.
            - [피해자], [받은계좌1], [인물1], [지역1] 같은 대괄호는 사람, 번호, 주소를 바꾼 이름표다. 이름표를 그대로 써라. 없는 이름표를 만들지 마라.
            - 누가 사기범인지는 문맥으로 판단해 글로 써라.
            - 근거는 조각 ID와, 그 조각 글에서 그대로 옮긴 짧은 인용문으로 대라. 글자를 고치지 마라.
            - dateText, timeText는 근거 조각에 적힌 날짜, 시각 표현을 그대로 옮겨라. 적혀 있지 않으면 null로 두고 추정하지 마라.
            - dateText, timeText를 옮겼으면 그 표현이 적힌 조각도 evidence에 넣어라. 근거에 없는 날짜, 시각은 버려진다.
            - 시각만 적힌 문장이 같은 서류 앞쪽에서 날짜를 밝힌 이야기의 이어짐이면 그 날짜 표현을 dateText로 옮기고, 날짜가 적힌 조각도 evidence에 넣어라.
              "다음 날"처럼 날이 바뀌었거나 어느 날인지 문맥으로 분명하지 않으면 null로 둬라.
            JSON 하나로만 답하라:
            {"events": [{"what": "사건 항목 한 문장", "dateText": "11.18" 또는 null, "timeText": "14:32" 또는 null,
              "evidence": [{"fragmentId": 123, "quote": "조각 글에서 옮긴 부분"}]}]}""";

    static final String SCHEMA = """
            {"type": "object", "additionalProperties": false, "required": ["events"],
             "properties": {"events": {"type": "array", "items": {
               "type": "object", "additionalProperties": false,
               "required": ["what", "dateText", "timeText", "evidence"],
               "properties": {
                 "what": {"type": "string"},
                 "dateText": {"anyOf": [{"type": "string"}, {"type": "null"}]},
                 "timeText": {"anyOf": [{"type": "string"}, {"type": "null"}]},
                 "evidence": {"type": "array", "items": {
                   "type": "object", "additionalProperties": false, "required": ["fragmentId", "quote"],
                   "properties": {"fragmentId": {"type": "integer"}, "quote": {"type": "string"}}}}}}}}}""";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LlmClient llm;

    public TimelineService(LlmClient llm) {
        this.llm = llm;
    }

    /** caseYear: 연도 없이 적힌 날짜("11.18")를 채울 사건 연도 */
    public TimelineResult build(List<EvidenceFragment> fragments, List<MaskLabel> labels, int caseYear) {
        List<EvidenceFragment> sent = fragments.stream().filter(f -> f.maskedText() != null).toList();
        String input = input(sent);
        List<LlmCall> calls = new ArrayList<>();
        String user = input;
        List<String> errors = List.of();
        for (int attempt = 0; attempt <= FORMAT_RETRIES; attempt++) {
            LlmReply reply;
            try {
                reply = llm.complete(SYSTEM, user, SCHEMA);
            } catch (LlmRefusedException e) {
                calls.add(new LlmCall(llm.provider(), null, PROMPT_VERSION, user, null));
                return new TimelineResult(null, "LLM 거절: " + e.getMessage(), calls);
            }
            String output = reply.text();
            calls.add(new LlmCall(llm.provider(), reply.model(), PROMPT_VERSION, user, output));
            JsonNode root = parse(output);
            errors = root == null ? List.of("JSON으로 읽을 수 없음") : shapeErrors(root);
            if (errors.isEmpty()) {
                Map<Long, String> texts = sent.stream()
                        .collect(Collectors.toMap(EvidenceFragment::id, EvidenceFragment::maskedText));
                List<TimelineEvent> events = toEvents(root, new EvidenceVerifier(texts),
                        new LabelLinker(labels, texts.values()), caseYear);
                return new TimelineResult(events, null, calls);
            }
            user = input + "\n\n## 지난 답의 형식 오류\n- " + String.join("\n- ", errors)
                    + "\n지시한 JSON 모양으로만 다시 답하라.";
        }
        return new TimelineResult(null, "형식 오류: " + String.join(", ", errors), calls);
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

    private static List<String> shapeErrors(JsonNode root) {
        List<String> errors = new ArrayList<>();
        JsonNode events = root.path("events");
        if (!events.isArray()) {
            return List.of("events 배열이 없음");
        }
        List<JsonNode> list = events.valueStream().toList();
        for (int i = 0; i < list.size(); i++) {
            JsonNode e = list.get(i);
            String at = "events[" + i + "]";
            if (!e.path("what").isString() || e.path("what").stringValue().isBlank()) {
                errors.add(at + ".what은 빈 칸이 아닌 문자열이어야 함");
            }
            for (String field : List.of("dateText", "timeText")) {
                JsonNode v = e.path(field);
                if (!v.isMissingNode() && !v.isNull() && !v.isString()) {
                    errors.add(at + "." + field + "는 문자열이나 null이어야 함");
                }
            }
            if (!e.path("evidence").isArray()) {
                errors.add(at + ".evidence 배열이 없음");
                continue;
            }
            List<JsonNode> evidence = e.path("evidence").valueStream().toList();
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
        return errors;
    }

    private static List<TimelineEvent> toEvents(JsonNode root, EvidenceVerifier verifier, LabelLinker linker, int caseYear) {
        List<TimelineEvent> events = new ArrayList<>();
        for (JsonNode e : root.path("events").valueStream().toList()) {
            List<Evidence> evidence = e.path("evidence").valueStream()
                    .map(ev -> verifier.verify(ev.path("fragmentId").longValue(), ev.path("quote").stringValue()))
                    .toList();
            String what = e.path("what").stringValue();
            List<String> unknown = linker.unknown(what);
            EvidenceStatus status = EvidenceVerifier.status(evidence);
            if (status == EvidenceStatus.VERIFIED && !unknown.isEmpty()) {
                status = EvidenceStatus.NEEDS_CHECK;
            }
            String dateText = shownIn(e.path("dateText"), evidence, verifier);
            String timeText = shownIn(e.path("timeText"), evidence, verifier);
            LocalDate date = dateText == null ? null : TimeNormalizer.date(dateText, caseYear);
            LocalTime time = timeText == null ? null : TimeNormalizer.time(timeText);
            events.add(new TimelineEvent(what, linker.display(what), date, time, status, evidence, unknown));
        }
        return sorted(events);
    }

    /**
     * 아는 날짜와 시각으로 정렬한다. 모르는 값은 정렬할 때만 LLM 순서상 바로 앞 항목의 값을 빌린다(화면에는 시각 불명 그대로).
     * 김우리 실제 호출(11-26)에서 LLM 순서는 맞았는데 모르는 항목을 맨 뒤로 보내자 13시 50분 전화가 16시 20분 신고 뒤로 밀렸다.
     * 시각은 같은 날짜를 빌렸을 때만 빌린다. 날짜만 아는 다음 날 항목이 앞날의 시각을 달고 서지 않게.
     */
    private static List<TimelineEvent> sorted(List<TimelineEvent> events) {
        record Keyed(TimelineEvent event, LocalDate date, LocalTime time) {
        }
        List<Keyed> keyed = new ArrayList<>();
        LocalDate prevDate = null;
        LocalTime prevTime = null;
        for (TimelineEvent e : events) {
            LocalDate date = e.date() != null ? e.date() : prevDate;
            LocalTime time = e.time() != null ? e.time() : Objects.equals(date, prevDate) ? prevTime : null;
            keyed.add(new Keyed(e, date, time));
            prevDate = date;
            prevTime = time;
        }
        keyed.sort(Comparator.comparing(Keyed::date, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(Keyed::time, Comparator.nullsFirst(Comparator.<LocalTime>naturalOrder())));
        return keyed.stream().map(Keyed::event).toList();
    }

    /** LLM이 옮긴 날짜, 시각 표현이 확인된 근거에 실제로 적혀 있을 때만 쓴다. 없으면 LLM의 추정이라 버린다. */
    private static String shownIn(JsonNode text, List<Evidence> evidence, EvidenceVerifier verifier) {
        return text.isString() && verifier.inVerified(evidence, text.stringValue()) ? text.stringValue() : null;
    }
}
