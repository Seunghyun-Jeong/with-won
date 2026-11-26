package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.service.AmountNormalizer.Amount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 근거와 요약 문장 속 금액 표현을 원 단위로 읽는다. 표현은 김우리 서류(신청서, 확인원, 이체내역)에 실제로 나온 꼴이다.
class AmountNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3,000,000원|3000000",
            "-9,500,000원|9500000",
            "300만원|3000000",
            "950만 원|9500000",
            "1,250만원|12500000",
            "3천만원|30000000",
            "3백만원|3000000",
            "1억 2,000만원|120000000",
            "1억원|100000000",
            "5천원|5000"})
    void 금액_표현을_원_단위로_읽는다(String text, long won) {
        assertThat(AmountNormalizer.find(text)).extracting(Amount::won).containsExactly(won);
    }

    @Test
    void 글_속_금액을_나온_순서대로_모두_찾고_부호는_뗀다() {
        assertThat(AmountNormalizer.find("금 12,500,000원 (3,000,000원, 9,500,000원 2회 이체) 피해, 잔액 -4,800원"))
                .containsExactly(new Amount("12,500,000원", 12_500_000), new Amount("3,000,000원", 3_000_000),
                        new Amount("9,500,000원", 9_500_000), new Amount("4,800원", 4_800));
    }

    @Test
    void 원이_붙지_않은_숫자는_금액이_아니다() {
        assertThat(AmountNormalizer.find("2026년 11월 18일 14:32 010-2847-5931 [받은계좌1] 잔액 2,733,700 공무원 2회")).isEmpty();
    }

    @Test
    void 숫자와_원_사이를_띄운_것은_원으로_시작하는_낱말이라_금액이_아니다() {
        assertThat(AmountNormalizer.find("15:10 원격제어 앱을 깔았고 서류 2 원본을 냈다")).isEmpty();
    }

    @Test
    void long을_넘는_숫자는_금액으로_보지_않고_건너뛴다() {
        assertThat(AmountNormalizer.find("1234567890123456789012원, 99999999999999억원, 300만원"))
                .extracting(Amount::won).containsExactly(3_000_000L);
    }

    @Test
    void 앞의_시각_숫자를_금액에_붙여_읽지_않는다() {
        assertThat(AmountNormalizer.find("11.18 14:32 3,000,000원")).extracting(Amount::won).containsExactly(3_000_000L);
    }
}
