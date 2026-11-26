package com.withwon.integration.ai.dto;

/**
 * 가림에 넣는 근거 조각 하나(fragment의 speaker, text).
 * speaker는 녹음이면 A, B, 카톡이면 SELF, OTHER, 서류 줄이면 null이다. 누가 번호를 물었고 누가 답했는지 가를 때 쓴다.
 */
public record MaskInput(String speaker, String text) {
}
