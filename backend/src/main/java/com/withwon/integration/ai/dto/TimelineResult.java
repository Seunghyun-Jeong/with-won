package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 타임라인 작업 결과. 성공하면 events가 시각순이고 failReason은 null,
 * 형식 오류로 끝내 실패하면 events는 null이고 failReason에 마지막 오류를 담는다(ai_job.fail_kind=FORMAT, 직원-8).
 * calls는 성공, 실패와 상관없이 부른 순서대로의 호출 기록이다.
 */
public record TimelineResult(List<TimelineEvent> events, String failReason, List<LlmCall> calls) {
}
