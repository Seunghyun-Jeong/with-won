package com.withwon.integration.ai.dto;

/**
 * AI가 댄 근거 하나(finding_evidence 한 행). fragmentId는 보낸 조각에 없는 ID였으면 null이다.
 * verified는 조각이 보낸 조각에 있고 인용문이 그 가린 글에 들어 있을 때만 true다(직원-9).
 */
public record Evidence(Long fragmentId, String quote, boolean verified) {
}
