package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.SummaryResult;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 가린 근거 조각으로 담당자가 사건을 처음 열 때 볼 요약 문장과 확인 필요 항목을 만든다(직원-16, finding SUMMARY와 CHECK, 11-26).
 * 문장마다 근거를 받아 타임라인과 같은 규칙으로 대조하고(직원-9) 이름표를 잇는다. 확정은 담당자가 한다.
 *
 * <p>입력은 타임라인 결과가 아니라 타임라인과 같은 원본 조각이다. 타임라인 결과를 넣으면 근거가 LLM이 쓴 문장이 돼 원문 대조를 못 하고,
 * 담당자가 타임라인을 고칠 때마다(직원-19) 요약을 다시 돌려야 한다. 같은 입력 토큰을 한 번 더 내는 값(김우리 요약 입력 6,623 토큰, 약 $0.013, 11-26 실측)은 받아들였다.
 *
 * <p>피해액과 거래 건수는 이 작업이 내지 않는다. 계산은 코드가 하므로 요약 카드가 확정 피해 거래(case_transaction, 직원-27)로 낸다(직원-41과 같은 원칙).
 * 문장 속 금액은 확인된 근거에 같은 값이 있을 때만 믿고, LLM이 더한 합계는 확인 필요로 내린다(EvidenceVerifier.unverifiedAmounts).
 * 과실이나 배상 같은 판단은 쓰지 않게 한다. 결정은 사람이 한다.
 *
 * <p>요약 문장이 하나도 없는 답은 형식 오류로 본다. 빈 요약 카드보다 '분석 실패'로 보여 담당자가 원본으로 보게 하는 편이 낫다.
 * 확인 필요 항목은 없을 수 있다. 문장 수(3~5개)는 지시문으로만 정하고 코드가 막지 않는다.
 */
public class SummaryService {

    public static final String PROMPT_VERSION = "summary-v1";
    private static final String SYSTEM = """
            너는 보이스피싱 피해구제 심사를 돕는 정리 담당이다. 아래 근거 조각을 읽고 담당자가 사건을 처음 열 때 볼 요약과 확인 필요 항목을 써라.
            """ + LlmTask.READING_RULES + """
            - summary는 3~5문장이다. 사칭한 기관과 수법, 피해자가 속아서 한 일(앱 설치, 이체 등), 피해를 알아챈 뒤 한 일(신고 등) 순서로 써라.
            - 문장마다 그 내용이 적힌 조각을 evidence로 대라.
            - 금액은 근거에 적힌 그대로 옮겨라. 금액을 더하거나 건수를 세지 마라. 피해액 합계와 거래 건수는 시스템이 원장으로 따로 보여 준다.
            - checks에는 담당자가 원본에서 확인해야 할 점을 써라. 서류끼리 다른 시각, 금액, 계좌, 사람, 서로 맞지 않는 진술, 흐리거나 잘려 읽기 어려운 글 같은 것이다.
              서로 다른 점은 양쪽 조각을 모두 evidence로 대라. 없으면 빈 배열로 둬라.
            - 과실이나 배상 여부 같은 판단은 쓰지 마라. 판단은 담당자가 한다.
            JSON 하나로만 답하라:
            {"summary": [{"text": "요약 문장", "evidence": [{"fragmentId": 123, "quote": "조각 글에서 옮긴 부분"}]}],
              "checks": [{"text": "확인할 점", "evidence": [{"fragmentId": 123, "quote": "조각 글에서 옮긴 부분"}]}]}""";

    private static final String ITEM = """
            {"type": "object", "additionalProperties": false, "required": ["text", "evidence"],
               "properties": {"text": {"type": "string"}, "evidence": %s}}""".formatted(LlmTask.EVIDENCE_SCHEMA);
    static final String SCHEMA = """
            {"type": "object", "additionalProperties": false, "required": ["summary", "checks"],
             "properties": {"summary": {"type": "array", "items": %1$s}, "checks": {"type": "array", "items": %1$s}}}"""
            .formatted(ITEM);

    private final LlmTask task;

    public SummaryService(LlmClient llm) {
        this.task = new LlmTask(llm, SYSTEM, SCHEMA, PROMPT_VERSION, SummaryService::shapeErrors);
    }

    public SummaryResult summarize(List<EvidenceFragment> fragments, List<MaskLabel> labels) {
        LlmTask.Run run = task.run(fragments, labels);
        if (run.answer() == null) {
            return new SummaryResult(null, null, run.failReason(), run.calls());
        }
        return new SummaryResult(items(run, "summary"), items(run, "checks"), null, run.calls());
    }

    private static List<String> shapeErrors(JsonNode root) {
        List<String> errors = new ArrayList<>();
        for (String field : List.of("summary", "checks")) {
            JsonNode items = root.path(field);
            if (!items.isArray()) {
                errors.add(field + " 배열이 없음");
                continue;
            }
            List<JsonNode> list = items.valueStream().toList();
            for (int i = 0; i < list.size(); i++) {
                LlmTask.itemErrors(list.get(i), "text", field + "[" + i + "]", errors);
            }
        }
        if (root.path("summary").isArray() && root.path("summary").size() == 0) {
            errors.add("summary에 문장이 하나 이상 있어야 함");
        }
        return errors;
    }

    private static List<FindingText> items(LlmTask.Run run, String field) {
        return run.answer().path(field).valueStream().map(item -> run.check(item, "text")).toList();
    }
}
