package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 요약 작업 결과. 성공하면 sentences(SUMMARY)와 checks(CHECK)가 LLM이 쓴 순서이고 failReason은 null,
 * 실패하면 둘 다 null이고 failReason에 이유를 담는다(직원-8). calls는 성공, 실패와 상관없이 부른 순서대로의 호출 기록이다.
 */
public record SummaryResult(List<SummaryItem> sentences, List<SummaryItem> checks, String failReason, List<LlmCall> calls) {
}
