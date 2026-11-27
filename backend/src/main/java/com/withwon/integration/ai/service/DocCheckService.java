package com.withwon.integration.ai.service;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.dto.DocCheck;
import com.withwon.integration.ai.dto.DocCheckResult;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.FindingText;
import com.withwon.integration.ai.dto.MaskLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 가린 근거 조각에서 서류마다 보완이 필요할 수 있는 이유를 근거와 함께 찾는다(직원-24 AI 서류 확인 요약, finding DOC_CHECK, 11-28).
 * 이유마다 요약, 태깅과 같은 규칙으로 근거를 대조한다(직원-9).
 *
 * <p>LLM은 이유만 쓴다. '갖춤'과 '보완 필요'는 코드가 정하고(DocCheck.needsSupplement) 최종 판단과 보완 요청은 담당 직원이 한다(직원-30).
 * '갖춤'은 인용할 근거가 없어 대조할 수 없으므로 LLM에게 묻지 않는다(태깅의 notFound와 같은 원리).
 *
 * <p>이 작업이 보지 않는 것:
 * 필수 서류가 있는지는 규칙(백엔드 한 곳)이 본다. 읽기 실패(흐린 사진, 글자 0개)는 읽기 단계가 '읽을 수 없음'으로 낸다(직원-8).
 * LLM은 가린 글만 보고 신분증은 받지 않으므로, 글로 보이는 깨진 곳만 짚을 수 있다.
 * 송금 금액, 시각, 받은 계좌가 서류끼리 다른 것은 거래 기록 대조(직원-27)가 규칙으로 하므로 빼라고 지시한다.
 * 서류끼리 다른 값과 읽기 어려운 글은 요약의 확인 필요(summary-v2)에서 빼 이 작업 한 곳에서만 나온다.
 *
 * <p>서류 전체를 한 호출에 준다. 불일치는 두 서류를 함께 봐야 보이고, 서류별로 부르면 같은 사건 글을 나눠 여러 번 보낸다.
 * 대상 서류(finding.document_id)는 하나라 LLM이 보완을 요청할 서류를 입력의 서류 이름으로 고르게 한다.
 * 목록 밖 이름은 형식 오류로 다시 묻고(Claude는 스키마 enum이 먼저 막음), 확인된 근거에 대상 서류의 조각이 없으면
 * 엉뚱한 서류에 붙었을 수 있어 확인 필요로 내린다.
 */
public class DocCheckService {

    public static final String PROMPT_VERSION = "doc-check-v1";
    private static final String SYSTEM = """
            너는 보이스피싱 피해구제 심사를 돕는 정리 담당이다. 아래 근거 조각을 서류별로 읽고, 담당자가 보완을 요청할지 볼 만한 점을 서류마다 근거와 함께 찾아라.
            """ + LlmTask.READING_RULES + """
            - 찾을 것: 신청인이 적어야 하는데 비어 있는 칸, 서류끼리 다른 사람, 날짜, 사건 번호, 기관, 글이 깨지거나 끊겨 뜻을 알 수 없는 곳.
            - 같은 이름표는 같은 값이고 다른 이름표는 다른 값이다.
            - 송금 금액, 송금 시각, 받은 계좌가 서류끼리 다른 것은 쓰지 마라. 시스템이 은행 기록으로 따로 대조한다.
            - 서식에 신청인이 적지 않는다고 적힌 칸(접수번호, 접수일자 등)은 비어 있어도 쓰지 마라.
            - document에는 보완을 요청할 서류 이름을 입력의 "## 서류:" 이름 그대로 써라. 서로 다른 점은 양쪽 조각을 모두 evidence로 대라.
            - 문제가 없는 서류는 넣지 마라. '갖춤' 같은 판단이나 과실, 배상 여부는 쓰지 마라. 판단은 담당자가 한다.
            JSON 하나로만 답하라:
            {"issues": [{"document": "서류 이름", "text": "보완이 필요할 수 있는 이유", "evidence": [{"fragmentId": 123, "quote": "조각 글에서 옮긴 부분"}]}]}""";

    private final LlmClient llm;

    public DocCheckService(LlmClient llm) {
        this.llm = llm;
    }

    public DocCheckResult check(List<EvidenceFragment> fragments, List<MaskLabel> labels) {
        List<EvidenceFragment> sent = fragments.stream().filter(f -> f.maskedText() != null).toList();
        List<String> documents = sent.stream().map(EvidenceFragment::document).distinct().toList();
        LlmTask.Run run = new LlmTask(llm, SYSTEM, schema(documents), PROMPT_VERSION, root -> shapeErrors(root, documents))
                .run(fragments, labels);
        if (run.answer() == null) {
            return new DocCheckResult(null, run.failReason(), run.calls());
        }
        Map<Long, String> documentOf = sent.stream().collect(toMap(EvidenceFragment::id, EvidenceFragment::document));
        List<JsonNode> answered = run.answer().path("issues").valueStream().toList();
        List<DocCheck> checks = documents.stream().map(d -> {
            List<FindingText> issues = answered.stream().filter(i -> i.path("document").stringValue().equals(d))
                    .map(i -> onTarget(run.check(i, "text"), d, documentOf)).toList();
            return new DocCheck(d, issues.stream().anyMatch(f -> f.status() != EvidenceStatus.NO_EVIDENCE), issues);
        }).toList();
        return new DocCheckResult(checks, null, run.calls());
    }

    /** 확인된 근거에 대상 서류의 조각이 없으면 VERIFIED를 확인 필요로 내린다 */
    private static FindingText onTarget(FindingText f, String document, Map<Long, String> documentOf) {
        boolean cited = f.evidence().stream().filter(Evidence::verified)
                .anyMatch(e -> Objects.equals(documentOf.get(e.fragmentId()), document));
        if (cited || f.status() != EvidenceStatus.VERIFIED) {
            return f;
        }
        return new FindingText(f.text(), f.displayText(), EvidenceStatus.NEEDS_CHECK, f.evidence(), f.unknownLabels(),
                f.unverifiedAmounts());
    }

    private static String schema(List<String> documents) {
        return """
                {"type": "object", "additionalProperties": false, "required": ["issues"],
                 "properties": {"issues": {"type": "array", "items": {
                   "type": "object", "additionalProperties": false, "required": ["document", "text", "evidence"],
                   "properties": {"document": {"type": "string", "enum": %s}, "text": {"type": "string"}, "evidence": %s}}}}}"""
                .formatted(documents.stream().map(d -> "\"" + d + "\"").collect(joining(", ", "[", "]")), LlmTask.EVIDENCE_SCHEMA);
    }

    private static List<String> shapeErrors(JsonNode root, List<String> documents) {
        List<String> errors = new ArrayList<>();
        if (!root.path("issues").isArray()) {
            errors.add("issues 배열이 없음");
            return errors;
        }
        List<JsonNode> issues = root.path("issues").valueStream().toList();
        for (int i = 0; i < issues.size(); i++) {
            JsonNode document = issues.get(i).path("document");
            if (!document.isString() || !documents.contains(document.stringValue())) {
                errors.add("issues[" + i + "].document " + document + "는 입력의 서류 이름이 아님");
            }
            LlmTask.itemErrors(issues.get(i), "text", "issues[" + i + "]", errors);
        }
        return errors;
    }
}
