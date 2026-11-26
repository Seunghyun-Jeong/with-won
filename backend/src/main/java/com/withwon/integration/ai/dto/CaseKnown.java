package com.withwon.integration.ai.dto;

import java.util.List;

/**
 * 사건에서 이미 아는 사람, 번호, 주소. 가림 입구(MaskService.maskForLlm)가 받는 한 벌이고,
 * 번호 가림에 넘기는 MaskKnown도 여기서 만든다(아는 계좌를 두 곳에 적지 않게).
 * payees는 원장의 피해 송금 시각 순서다. 이 순서가 [받은계좌n], [예금주n]의 번호가 된다.
 * branches는 "우리은행 왕십리지점"처럼 은행 이름과 지점 이름이다.
 */
public record CaseKnown(List<String> rrns, String victimName, String victimPhone, String victimEmail,
        String victimAddress, List<String> victimAccounts, List<Payee> payees, List<String> branches) {

    public record Payee(String account, String holder) {
    }

    public MaskKnown maskKnown() {
        return new MaskKnown(rrns, java.util.stream.Stream.concat(victimAccounts.stream(),
                payees.stream().map(Payee::account)).toList());
    }
}
