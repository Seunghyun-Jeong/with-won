package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 사건에서 이미 아는 값. 하이픈이나 띄어쓰기는 상관없이 숫자만 비교한다.
 * rrns: 신분증 등에서 아는 주민번호. 가린 뒤에도 남았는지 재검사한다(뒷자리 7자리만 남아도 실패).
 * keepAccounts: 원장, 신청서에 있는 계좌. 긴 숫자는 기본으로 가리지만 이 계좌는 남긴다.
 */
public record MaskKnown(List<String> rrns, List<String> keepAccounts) {
}
