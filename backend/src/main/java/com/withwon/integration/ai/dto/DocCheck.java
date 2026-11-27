package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 서류 하나의 점검 요약(직원-24). issues는 그 서류를 대상으로 LLM이 근거와 함께 쓴 보완 이유(finding DOC_CHECK, document_id가 이 서류)다.
 * needsSupplement는 코드가 정한다. 근거 부족이 아닌 이유가 하나라도 있으면 true('보완 필요'), 없으면 false('갖춤')다.
 * 최종 판단과 보완 요청은 담당 직원이 한다(직원-30).
 */
public record DocCheck(String document, boolean needsSupplement, List<FindingText> issues) {
}
