package com.withwon.integration.ai.dto;

/** finding.evidence_status. 확률이 아니라 근거 대조 결과다(직원-18). */
public enum EvidenceStatus {
    /** 근거가 모두 확인됨 */
    VERIFIED,
    /** 일부 근거만 확인되거나, 대응표로 잇지 못한 이름표가 있음 */
    NEEDS_CHECK,
    /** 확인된 근거가 하나도 없음 */
    NO_EVIDENCE
}
