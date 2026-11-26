package com.withwon.integration.ai.service;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.withwon.integration.ai.client.LlmClient;
import com.withwon.integration.ai.dto.CheckItem;
import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.TagItem;
import com.withwon.integration.ai.dto.TagResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 가린 근거 조각에서 자율배상 점검 항목에 해당하는 피해자 행위와 사건 정황을 근거와 함께 찾는다
 * (직원-35 제외 사유 신호 finding SIGNAL, 직원-36 과실 행위 finding BEHAVIOR, 11-26). 태그마다 요약과 같은 규칙으로 근거를 대조한다(직원-9).
 *
 * <p>LLM은 찾기만 한다. 제외나 과실 판정(check_result)과 답과 다름은 시트 규칙대로 코드가 하고, 결정은 담당자가 한다.
 * 그래서 자율배상 신청서의 문항 답은 넣지 않는다. 규칙은 "신청서 답을 빼고 낸 판정"과 답을 견주는데, 답을 보여 주면 LLM이 답에 맞는 근거를 찾아온다.
 * 신청서의 글 칸(경위, 참고사항)은 다른 서류처럼 조각으로 들어온다.
 *
 * <p>넘긴 항목을 한 호출에 준다. 단계별로 나누면 호출마다 같은 원본(김우리 약 6.6천 토큰)을 다시 보내 입력 비용이 단계 수만큼 늘고,
 * "링크를 눌러 앱을 받았다"처럼 단계에 걸친 문장을 호출마다 따로 인용한다.
 * 항목 목록은 지시문이 아니라 입력 앞에 붙인다. check_item 시드가 바뀌면 달라지므로 호출 기록(ai_call_log.input)에 남아야 한다.
 *
 * <p>답은 찾은 것만 받는다. 없다는 말은 인용할 수 없어 대조할 수 없으므로 LLM에게 묻지 않고, 못 찾은 항목은 넘긴 목록에서 코드가 정한다(notFound).
 * 같은 항목이 여러 번 와도 받는다. 직접 이체(EX_SELF_ORDER)처럼 거래에 붙는 항목은 이체마다 한 태그이고,
 * 어느 피해 거래(case_transaction)인지 잇는 일은 원장이 생기면 한다.
 * 넘긴 목록에 없는 코드는 버리지 않고 형식 오류로 다시 묻는다. 버리면 조용히 사라지고, 목록에 없는 코드는 목록을 잘못 읽었다는 신호라 답 전체를 믿기 어렵다.
 * Claude는 스키마의 enum으로 API가 먼저 막는다.
 */
public class TaggingService {

    public static final String PROMPT_VERSION = "tagging-v1";
    private static final String SYSTEM = """
            너는 보이스피싱 피해구제 심사를 돕는 정리 담당이다. 입력 맨 앞의 점검 항목마다, 아래 근거 조각에 그 항목에 해당하는 피해자 행위나 사건 정황이 적혀 있으면 근거와 함께 찾아라.
            """ + LlmTask.READING_RULES + """
            - itemCode에는 점검 항목 목록의 코드만 써라.
            - 서류에 적혀 있는 것만 찾아라. 적혀 있지 않은 항목은 넣지 마라. 하지 않았다는 진술도 넣지 마라.
            - 사기범이 요구한 글만 있고 피해자가 실제로 했다는 글이 없으면 넣지 마라. 했다는 글이 있으면 요구한 글도 함께 근거로 대도 된다.
            - text는 누가 무엇을 했는지 한 문장으로 써라. 금액은 근거에 적힌 그대로 옮겨라.
            - 한 항목의 근거가 여러 조각이면 한 태그에 모두 대라. 이체마다 따로 일어난 일은 이체마다 따로 써라.
            - 제외나 과실 여부, 배상 비율 같은 판단은 쓰지 마라. 판단은 담당자가 한다.
            JSON 하나로만 답하라:
            {"tags": [{"itemCode": "점검 항목 코드", "text": "찾은 행위나 정황", "evidence": [{"fragmentId": 123, "quote": "조각 글에서 옮긴 부분"}]}]}""";

    private final LlmClient llm;

    public TaggingService(LlmClient llm) {
        this.llm = llm;
    }

    /** items: 태깅할 점검 항목(판정이 서류나 기록+서류인 것), 화면 순서대로 */
    public TagResult tag(List<CheckItem> items, List<EvidenceFragment> fragments, List<MaskLabel> labels) {
        List<String> codes = items.stream().map(CheckItem::code).toList();
        LlmTask.Run run = new LlmTask(llm, SYSTEM, schema(codes), PROMPT_VERSION, root -> shapeErrors(root, codes))
                .run(itemList(items), fragments, labels);
        if (run.answer() == null) {
            return new TagResult(null, null, run.failReason(), run.calls());
        }
        Map<String, CheckItem.Kind> kinds = items.stream().collect(toMap(CheckItem::code, CheckItem::kind));
        List<TagItem> tags = run.answer().path("tags").valueStream()
                .map(t -> {
                    String code = t.path("itemCode").stringValue();
                    return new TagItem(code, kinds.get(code), run.check(t, "text"));
                })
                .sorted(Comparator.comparingInt(t -> codes.indexOf(t.itemCode())))
                .toList();
        Set<String> found = tags.stream().filter(t -> t.finding().status() != EvidenceStatus.NO_EVIDENCE)
                .map(TagItem::itemCode).collect(toSet());
        return new TagResult(tags, codes.stream().filter(c -> !found.contains(c)).toList(), null, run.calls());
    }

    private static String itemList(List<CheckItem> items) {
        return items.stream().map(i -> "- " + i.code() + ": " + i.name() + "\n").collect(joining("", "## 점검 항목\n", "\n"));
    }

    private static String schema(List<String> codes) {
        return """
                {"type": "object", "additionalProperties": false, "required": ["tags"],
                 "properties": {"tags": {"type": "array", "items": {
                   "type": "object", "additionalProperties": false, "required": ["itemCode", "text", "evidence"],
                   "properties": {"itemCode": {"type": "string", "enum": %s}, "text": {"type": "string"}, "evidence": %s}}}}}"""
                .formatted(codes.stream().map(c -> "\"" + c + "\"").collect(joining(", ", "[", "]")), LlmTask.EVIDENCE_SCHEMA);
    }

    private static List<String> shapeErrors(JsonNode root, List<String> codes) {
        List<String> errors = new ArrayList<>();
        if (!root.path("tags").isArray()) {
            errors.add("tags 배열이 없음");
            return errors;
        }
        List<JsonNode> tags = root.path("tags").valueStream().toList();
        for (int i = 0; i < tags.size(); i++) {
            JsonNode code = tags.get(i).path("itemCode");
            if (!code.isString() || !codes.contains(code.stringValue())) {
                errors.add("tags[" + i + "].itemCode " + code + "는 점검 항목 목록에 없음");
            }
            LlmTask.itemErrors(tags.get(i), "text", "tags[" + i + "]", errors);
        }
        return errors;
    }
}
