package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 요약 문장(finding kind=SUMMARY)이나 확인 필요 항목(kind=CHECK) 하나(직원-16).
 * text는 LLM이 쓴 그대로(이름표 포함, finding.value로 저장하고 바꾸지 않음), displayText는 코드가 이름표를 실제 값으로 이은 담당자용 글이다.
 * unknownLabels는 보낸 글에 없어 잇지 못한 이름표, unverifiedAmounts는 확인된 근거에 같은 값이 없는 금액 표현이다.
 * 근거가 모두 맞아도 둘 중 하나가 있으면 확인 필요다.
 */
public record SummaryItem(String text, String displayText, EvidenceStatus status, List<Evidence> evidence,
        List<String> unknownLabels, List<String> unverifiedAmounts) {
}
