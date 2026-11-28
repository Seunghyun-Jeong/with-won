package com.withwon.domain.transaction;

/** transactions.channel 값의 주인. 자율배상 판정(직원-35)과 모의 원장이 같이 쓴다. */
public enum TransactionChannel {
    /** 앱 */
    MOBILE,
    /** 앱으로 한 ATM 출금 */
    APP_ATM,
    /** 카드로 한 ATM 출금 */
    ATM,
    /** 창구 */
    BRANCH,
    /** 카드 결제 */
    CARD
}
