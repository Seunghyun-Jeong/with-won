package com.withwon.integration.ai.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 근거에 적힌 날짜, 시각 표현을 코드가 정규화한다(직원-6, 11-26 T1). LLM은 표현을 옮기기만 하고 연도나 오전/오후를 채우지 않는다.
 * 모호하면 추정하지 않고 null(시각 불명)이다.
 *
 * <p>날짜: 연도가 없으면 사건 연도로 채운다. 연말 사건의 1월 표현은 틀릴 수 있지만 김우리처럼 한 해 안의 사건이 대부분이라 받아들인다.
 * <p>시각: 오전/오후가 있으면 12시간제, 없으면 두 자리 시("09:00", "14:32", 거래내역과 휴대폰 화면)나 13시 이상만 24시간제로 읽는다.
 * "1:56", "2시"처럼 한 자리 시만 있으면 새벽인지 낮인지 모르므로 null이다.
 * "오후 1시 50분경"의 경, 쯤은 떼고 읽는다. 피해자가 서류에 직접 적은 시각이라 AI 추정이 아니고, 확정은 담당자가 한다(11-26).
 */
final class TimeNormalizer {

    private static final Pattern DATE = Pattern.compile(
            "^(?:(\\d{4})\\s*[년.\\-/]\\s*)?(\\d{1,2})\\s*[월.\\-/]\\s*(\\d{1,2})\\s*[일.]?(?:\\s*\\(.\\))?$");
    private static final Pattern TIME = Pattern.compile(
            "^(오전|오후|AM|PM)?\\s*(\\d{1,2})\\s*(?::\\s*(\\d{2})|시(?:\\s*(\\d{1,2})\\s*분)?)\\s*(?:경|쯤)?$", Pattern.CASE_INSENSITIVE);

    private TimeNormalizer() {
    }

    static LocalDate date(String text, int caseYear) {
        Matcher m = DATE.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        try {
            int year = m.group(1) == null ? caseYear : Integer.parseInt(m.group(1));
            return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    static LocalTime time(String text) {
        Matcher m = TIME.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        String half = m.group(1);
        String hourText = m.group(2);
        int hour = Integer.parseInt(hourText);
        String minuteText = m.group(3) != null ? m.group(3) : m.group(4);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (half != null) {
            if (hour < 1 || hour > 12) {
                return null;
            }
            boolean pm = half.equals("오후") || half.equalsIgnoreCase("PM");
            hour = hour % 12 + (pm ? 12 : 0);
        } else if (hourText.length() < 2 && hour < 13) {
            return null;
        }
        try {
            return LocalTime.of(hour, minute);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
