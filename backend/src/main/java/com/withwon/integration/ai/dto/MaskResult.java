package com.withwon.integration.ai.dto;

import java.util.List;

/** texts: 조각과 같은 순서의 가린 글(실패 조각은 null). labels: 기존 대응표에 이번에 새로 매긴 것을 끝에 붙인 전체. */
public record MaskResult(List<String> texts, List<MaskLabel> labels) {
}
