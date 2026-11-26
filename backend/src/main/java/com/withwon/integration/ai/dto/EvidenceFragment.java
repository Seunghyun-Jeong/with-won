package com.withwon.integration.ai.dto;

/**
 * LLM에 보낼 근거 조각 하나(fragment의 id, speaker, start_ms, masked_text와 서류 이름).
 * maskedText가 null이면 가림에 실패한 조각이라 보내지 않는다(직원-7).
 * document는 LLM이 서류별로 묶어 읽게 하는 이름이다("통화녹음1", "이체내역캡처").
 */
public record EvidenceFragment(long id, String document, String speaker, Integer startMs, String maskedText) {
}
