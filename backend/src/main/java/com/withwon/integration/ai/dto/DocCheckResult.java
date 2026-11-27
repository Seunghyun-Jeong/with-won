package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 서류 점검 요약 작업 결과. 성공하면 documents는 보낸 서류마다 하나씩 입력 순서대로이고 failReason은 null이다.
 * 실패하면 documents가 null이고 failReason에 이유를 담는다(직원-8). calls는 성공, 실패와 상관없이 부른 순서대로의 호출 기록이다.
 */
public record DocCheckResult(List<DocCheck> documents, String failReason, List<LlmCall> calls) {
}
