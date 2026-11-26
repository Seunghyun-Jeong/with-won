package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// 근거에 적힌 날짜, 시각 표현을 정규화한다. 모호하면 추정하지 않고 null(직원-6).
class TimeNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026년 11월 18일|2026-11-18",
            "2026년 11월 18일 (수)|2026-11-18",
            "11월 18일|2026-11-18",
            "11.18|2026-11-18",
            "2026.11.18|2026-11-18",
            "2026. 11. 18.|2026-11-18",
            "2026-11-18|2026-11-18",
            "11/18|2026-11-18",
            "2025년 12월 31일|2025-12-31"})
    void 날짜_표현을_읽고_연도가_없으면_사건_연도로_채운다(String text, LocalDate expected) {
        assertThat(TimeNormalizer.date(text, 2026)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"어제", "13.45", "2월 30일", "11", "지난주 화요일", ""})
    void 날짜로_읽을_수_없으면_null이다(String text) {
        assertThat(TimeNormalizer.date(text, 2026)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "14:32|14:32",
            "09:00|09:00",
            "오후 1:56|13:56",
            "오전 9시|09:00",
            "오후 2시 20분|14:20",
            "19시 22분|19:22",
            "오후 12:10|12:10",
            "오전 12:10|00:10",
            "PM 3:05|15:05",
            "오후 1시 50분경|13:50",
            "14:32경|14:32",
            "오전 9시쯤|09:00"})
    void 시각_표현을_24시간으로_읽는다(String text, LocalTime expected) {
        assertThat(TimeNormalizer.time(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1:56", "2시", "2시쯤", "25:00", "오후 13:10", "점심때", ""})
    void 오전_오후가_모호하거나_읽을_수_없으면_null이다(String text) {
        assertThat(TimeNormalizer.time(text)).isNull();
    }
}
