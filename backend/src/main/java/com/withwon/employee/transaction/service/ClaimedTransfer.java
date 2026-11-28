package com.withwon.employee.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 서류가 말하는 송금 한 건. source는 그 값을 적은 서류(피해구제신청서, 사건사고사실확인원 등). */
public record ClaimedTransfer(String source, BigDecimal amount, LocalDateTime at, String bank, String account) {
}
