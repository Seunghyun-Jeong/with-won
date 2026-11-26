package com.withwon.integration.ai.dto;

/**
 * 이름표와 실제 값 한 쌍(mask_label 한 행). 사건마다 새로 매기고 은행 안에만 둔다.
 * LLM 답에 나온 이름표를 담당자 화면에서 실제 값으로 잇는 데 쓴다.
 */
public record MaskLabel(String label, Kind kind, String value) {

    public enum Kind { PERSON, ACCOUNT, PHONE, EMAIL, ADDRESS, REGION }
}
