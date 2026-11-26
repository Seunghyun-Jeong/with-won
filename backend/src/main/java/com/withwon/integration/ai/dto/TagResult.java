package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 태깅 작업 결과. 성공하면 tags는 넘긴 점검 항목 순서(같은 항목 안에서는 LLM이 쓴 순서)이고 failReason은 null이다.
 * notFound는 확인된 근거가 있는 태그가 하나도 없는 항목 코드로, 화면에 "서류에서 찾지 못함"으로 보인다.
 * 실패하면 tags와 notFound가 null이고 failReason에 이유를 담는다(직원-8). calls는 성공, 실패와 상관없이 부른 순서대로의 호출 기록이다.
 */
public record TagResult(List<TagItem> tags, List<String> notFound, String failReason, List<LlmCall> calls) {
}
