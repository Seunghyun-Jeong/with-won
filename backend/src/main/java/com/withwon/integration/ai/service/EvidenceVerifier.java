package com.withwon.integration.ai.service;

import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceStatus;
import java.util.List;
import java.util.Map;

/**
 * AI가 댄 근거를 대조하는 한 곳(직원-9). 타임라인, 요약, 과실 근거, 보고서가 같이 쓴다.
 * 기준은 이번 호출에 실제로 보낸 가린 글이다. 가림에 실패해 보내지 않은 조각과 다른 사건의 조각은 없는 ID로 본다.
 * 인용문은 공백 차이만 무시하고 포함 여부로 본다. OCR과 STT가 띄어쓰기를 들쭉날쭉하게 내고 LLM도 공백을 바꿔 옮기기 때문이다.
 * 글자를 바꾼 인용문(실제 이름, 고친 맞춤법)은 확인되지 않은 것으로 둔다.
 */
final class EvidenceVerifier {

    private final Map<Long, String> sent;

    /** sent: 보낸 조각 ID와 그 가린 글 */
    EvidenceVerifier(Map<Long, String> sent) {
        this.sent = sent;
    }

    Evidence verify(long fragmentId, String quote) {
        String text = sent.get(fragmentId);
        if (text == null) {
            return new Evidence(null, quote, false);
        }
        return new Evidence(fragmentId, quote, contains(text, quote));
    }

    /** 확인된 근거의 가린 글 가운데 하나에 part가 들어 있는가. 날짜, 시각 표현이 근거에 실제로 있는지 볼 때 쓴다. */
    boolean inVerified(List<Evidence> evidence, String part) {
        return evidence.stream().filter(Evidence::verified).anyMatch(e -> contains(sent.get(e.fragmentId()), part));
    }

    static EvidenceStatus status(List<Evidence> evidence) {
        long verified = evidence.stream().filter(Evidence::verified).count();
        if (verified == 0) {
            return EvidenceStatus.NO_EVIDENCE;
        }
        return verified == evidence.size() ? EvidenceStatus.VERIFIED : EvidenceStatus.NEEDS_CHECK;
    }

    private static boolean contains(String text, String part) {
        String p = squash(part);
        return !p.isEmpty() && squash(text).contains(p);
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }
}
