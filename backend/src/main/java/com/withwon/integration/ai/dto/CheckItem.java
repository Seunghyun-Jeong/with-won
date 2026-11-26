package com.withwon.integration.ai.dto;

/**
 * 태깅에 넘기는 점검 항목 하나(check_item 한 행의 코드, 종류, 이름). 목록의 주인은 시트 "1. 요구사항정의서_자율배상 점검 항목" 탭이고 시드로 DB에 들어간다.
 * 태깅에는 판정이 서류나 기록+서류인 항목만 넘긴다. 데이터와 담당자가 판정하는 항목까지 주면 LLM이 "소송" 같은 낱말에 태그를 붙여 탭 규칙과 어긋난다.
 */
public record CheckItem(String code, Kind kind, String name) {

    public enum Kind {
        /** 적용 제외 사유(직원-35). 찾은 것은 finding kind=SIGNAL */
        EXCLUSION,
        /** 과실 항목(직원-36). 찾은 것은 finding kind=BEHAVIOR */
        FAULT
    }
}
