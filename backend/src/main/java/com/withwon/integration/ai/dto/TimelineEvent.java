package com.withwon.integration.ai.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 타임라인 항목 하나(finding kind=TIMELINE).
 * what은 LLM이 쓴 그대로(이름표 포함, finding.value로 저장하고 바꾸지 않음), displayWhat은 코드가 이름표를 실제 값으로 이은 담당자용 글이다.
 * date와 time은 근거에 적힌 표현을 코드가 정규화한 값이고, 알 수 없으면 null이다(직원-6, 추정으로 채우지 않음).
 * unknownLabels는 보낸 글에 없어 실제 값으로 잇지 못한 이름표다.
 */
public record TimelineEvent(String what, String displayWhat, LocalDate date, LocalTime time,
        EvidenceStatus status, List<Evidence> evidence, List<String> unknownLabels) {
}
