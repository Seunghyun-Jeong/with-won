package com.withwon.global.util;

public final class AccountNumbers {

    private AccountNumbers() {
    }

    /** 계좌는 하이픈과 공백 같은 꼴 차이를 무시하고 숫자만 비교한다(거래 대조, 자율배상 판정이 같이 씀). */
    public static String digits(String account) {
        return account == null ? null : account.replaceAll("\\D", "");
    }
}
