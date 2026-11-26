package com.withwon.integration.ai.dto;

/**
 * 점검 항목 하나에 붙은 태그(finding 한 행, item_code=itemCode). kind는 LLM이 아니라 넘긴 점검 항목의 종류다.
 * 제외나 과실 판정(check_result)은 하지 않는다. 판정은 시트 규칙대로 코드가 하고 결정은 담당자가 한다.
 */
public record TagItem(String itemCode, CheckItem.Kind kind, FindingText finding) {
}
