package com.withwon.integration.ai.service;

import com.withwon.integration.ai.dto.MaskLabel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LLM 답의 이름표를 대응표(mask_label)로 실제 값에 잇는다(직원-7). 답 원문은 바꾸지 않고 담당자용 글을 따로 만든다.
 *
 * <p>대괄호 낱말은 이번에 보낸 가린 글에 그대로 있어야 믿는다. 대응표에 있어도 보낸 글에 없던 이름표는
 * LLM이 번호 규칙을 보고 지어낸 것일 수 있어(예: 글에는 [받은계좌2]만 있는데 [받은계좌1]) 잇지 않는다.
 * 보낸 글에 있는데 대응표에 없는 것은 원문의 대괄호([Web발신], [서울중앙지검])나 되돌리지 않는 가림 라벨([가린 번호])이라 그대로 둔다.
 */
final class LabelLinker {

    private static final Pattern BRACKET = Pattern.compile("\\[[^\\[\\]]{1,20}]");

    private final Map<String, String> values;
    private final String sentText;

    LabelLinker(List<MaskLabel> labels, Collection<String> sentTexts) {
        this.values = labels.stream().collect(Collectors.toMap(MaskLabel::label, MaskLabel::value));
        this.sentText = String.join("\n", sentTexts);
    }

    /** 보낸 글에 없어 믿지 못하는 대괄호 낱말, 나온 순서대로 한 번씩 */
    List<String> unknown(String text) {
        return BRACKET.matcher(text).results().map(MatchResult::group).filter(t -> !sentText.contains(t)).distinct().toList();
    }

    /** 믿을 수 있는 이름표만 실제 값으로 바꾼 글 */
    String display(String text) {
        return BRACKET.matcher(text).replaceAll(m -> {
            String value = sentText.contains(m.group()) ? values.get(m.group()) : null;
            return Matcher.quoteReplacement(value != null ? value : m.group());
        });
    }
}
