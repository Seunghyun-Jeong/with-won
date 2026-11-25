package com.withwon.integration.ai.dto;

/**
 * 녹음에서 읽은 발화 한 구간. fragment 표의 kind=UTTERANCE 한 행이 되고, seq는 SpeechReadService가 돌려준 목록 순서(1부터)다.
 * speaker는 CLOVA Speech가 목소리로 나눈 A, B …이고, 누가 피해자인지는 담지 않는다.
 */
public record Utterance(String speaker, int startMs, int endMs, String text) {
}
