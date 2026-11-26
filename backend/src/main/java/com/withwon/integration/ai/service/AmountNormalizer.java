package com.withwon.integration.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * 글 속 금액 표현을 원 단위로 읽는다(직원-9 금액 대조, 11-26). 김우리 서류만 봐도 "9,500,000원", "-3,000,000원"(이체내역),
 * "300만원", "3천만원"을 섞어 쓰고 LLM도 단위를 바꿔 옮기므로, 같은 금액인지 원으로 맞춰 본다.
 *
 * <p>"원"이 붙은 것만 금액으로 본다. 날짜, 시각, 전화번호, 계좌번호, "2회"와 헷갈리지 않으려는 것이다.
 * 숫자 뒤의 억, 천만, 백만, 만, 천 단위를 곱해 더한다("1억 2,000만원"). 단위 없는 숫자는 맨 끝에만 올 수 있어
 * "14:32 3,000,000원"의 32를 붙이지 않는다. 숫자와 원 사이를 띄운 것("15:10 원격제어")은 원으로 시작하는 낱말이라 금액으로 보지 않고,
 * 단위 뒤의 띄어쓰기("950만 원")만 받는다. 이체내역의 부호(-)는 떼고 읽는다. long을 넘는 값은 OCR이 숫자를 이어 붙인 글로 보고 건너뛴다.
 *
 * <p>못 읽는 꼴: 원이 빠진 표 칸("잔액 2,733,700"), "3,000,000 원"처럼 숫자 바로 뒤를 띄운 것, 소수("1.5억원"), 한글 숫자("삼백만 원"),
 * 천과 백이 섞인 꼴("5천3백만원"은 3,005,000으로 잘못 읽음). 문장 쪽 금액을 못 읽으면 그 금액은 대조를 건너뛰고,
 * 근거 쪽 금액을 못 읽으면 같은 값의 문장 금액이 확인 필요로 내려간다. 김우리 서류와 신청서에는 이런 꼴이 없어 받아들였다.
 */
final class AmountNormalizer {

    /** text는 글에 적힌 그대로(부호 뺌), won은 원 단위 값 */
    record Amount(String text, long won) {
    }

    private static final String UNIT = "(?:억|천만|백만|만|천)";
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d,.])(?:\\d[\\d,]*\\s*" + UNIT + "\\s*)*\\d[\\d,]*(?:\\s*" + UNIT + "\\s*)?원");
    private static final Pattern PART = Pattern.compile("(\\d[\\d,]*)\\s*(" + UNIT + ")?");
    private static final Map<String, Long> UNITS = Map.of(
            "억", 100_000_000L, "천만", 10_000_000L, "백만", 1_000_000L, "만", 10_000L, "천", 1_000L);

    private AmountNormalizer() {
    }

    static List<Amount> find(String text) {
        List<Amount> found = new ArrayList<>();
        for (MatchResult m : AMOUNT.matcher(text).results().toList()) {
            try {
                found.add(new Amount(m.group(), won(m.group())));
            } catch (NumberFormatException | ArithmeticException e) {
                // long을 넘는 값. 요약 작업 전체가 예외로 멈추지 않게 이 금액만 건너뛴다.
            }
        }
        return found;
    }

    private static long won(String amount) {
        long total = 0;
        for (MatchResult p : PART.matcher(amount).results().toList()) {
            long unit = p.group(2) == null ? 1 : UNITS.get(p.group(2));
            total = Math.addExact(total, Math.multiplyExact(Long.parseLong(p.group(1).replace(",", "")), unit));
        }
        return total;
    }
}
