package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TimelineEvent;
import com.withwon.integration.ai.dto.TimelineResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 가린 근거 조각으로 사건 타임라인을 만든다(직원-6, 직원-9, 11-26).
 * LLM은 문맥을 읽어 사건 항목을 고르고 근거 조각 ID와 인용문, 근거에 적힌 날짜와 시각 표현을 옮긴다.
 * 누가 사기범인지도 LLM이 문맥으로 본다. 코드는 근거 대조(EvidenceVerifier), 이름표 잇기(LabelLinker),
 * 시각 정규화(TimeNormalizer)와 정렬을 하고, 확정은 담당자가 한다(직원-19).
 *
 * <p>입력 모양, 형식 오류 재시도, 거절 처리는 요약과 같이 쓰는 LlmTask에 있다.
 *
 * <p>경위 글은 첫 문장에만 날짜를 쓰고 뒤 문장은 시각만 쓰는 일이 많다(김우리 자율배상 신청서에서 11개 중 4개 항목이 날짜 없음, 11-26).
 * 앞 문장의 날짜를 쓰게 하되 코드가 채우지 않고 LLM이 날짜가 적힌 조각을 근거로 대게 했다(timeline-v3). 그래야 다른 날의 날짜가
 * 잘못 넘어오면 근거 대조에서 걸리고, 담당자가 날짜의 출처를 눌러 볼 수 있다.
 */
public class TimelineService {

    public static final String PROMPT_VERSION = "timeline-v3";
    private static final String SYSTEM = """
            너는 보이스피싱 피해구제 심사를 돕는 정리 담당이다. 아래 근거 조각을 읽고 사건이 일어난 순서대로 사건 항목을 뽑아라.
            """ + LlmTask.READING_RULES + """
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
                 "evidence": %s}}}}}""".formatted(LlmTask.EVIDENCE_SCHEMA);

    private final LlmTask task;

    public TimelineService(LlmClient llm) {
        this.task = new LlmTask(llm, SYSTEM, SCHEMA, PROMPT_VERSION, TimelineService::shapeErrors);
    }

    /** caseYear: 연도 없이 적힌 날짜("11.18")를 채울 사건 연도 */
    public TimelineResult build(List<EvidenceFragment> fragments, List<MaskLabel> labels, int caseYear) {
        LlmTask.Run run = task.run(fragments, labels);
        if (run.answer() == null) {
            return new TimelineResult(null, run.failReason(), run.calls());
        }
        return new TimelineResult(toEvents(run, caseYear), null, run.calls());
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
            LlmTask.itemErrors(e, "what", at, errors);
            for (String field : List.of("dateText", "timeText")) {
                JsonNode v = e.path(field);
                if (!v.isMissingNode() && !v.isNull() && !v.isString()) {
                    errors.add(at + "." + field + "는 문자열이나 null이어야 함");
                }
            }
        }
        return errors;
    }

    private static List<TimelineEvent> toEvents(LlmTask.Run run, int caseYear) {
        List<TimelineEvent> events = new ArrayList<>();
        for (JsonNode e : run.answer().path("events").valueStream().toList()) {
            List<Evidence> evidence = run.evidence(e);
            String what = e.path("what").stringValue();
            List<String> unknown = run.linker().unknown(what);
            EvidenceStatus status = EvidenceVerifier.status(evidence, !unknown.isEmpty());
            String dateText = shownIn(e.path("dateText"), evidence, run.verifier());
            String timeText = shownIn(e.path("timeText"), evidence, run.verifier());
            LocalDate date = dateText == null ? null : TimeNormalizer.date(dateText, caseYear);
            LocalTime time = timeText == null ? null : TimeNormalizer.time(timeText);
            events.add(new TimelineEvent(what, run.linker().display(what), date, time, status, evidence, unknown));
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
