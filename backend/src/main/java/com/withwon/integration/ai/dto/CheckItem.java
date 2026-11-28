package com.withwon.integration.ai.dto;

import java.util.Set;

/**
 * 점검 항목 하나(check_item 한 행의 코드, 종류, 이름과 판정에 쓰는 범위, 판정 방식, 해당이 되는 답). 목록의 주인은 시트
 * "1. 요구사항정의서_자율배상 점검 항목" 탭이고 시드로 DB에 들어간다.
 * 태깅에는 판정이 서류나 기록+서류인 항목만 넘긴다. 데이터와 담당자가 판정하는 항목까지 주면 LLM이 "소송" 같은 낱말에 태그를 붙여 탭 규칙과 어긋난다.
 * 판정(CompJudgeService)에는 판정 방식이 다른 항목까지 모두 넘긴다.
 *
 * <p>hitAnswers는 신청서 문항에서 이 항목이 해당이 되는 답이다(분실신고를 했나요? 는 "NO", 사진 저장은 "YES"). 문항이 없는 항목은 비어 있다.
 */
public record CheckItem(String code, Kind kind, String name, Scope scope, JudgeBy judgeBy, Set<String> hitAnswers) {

    public enum Kind {
        /** 적용 제외 사유(직원-35). 찾은 것은 finding kind=SIGNAL */
        EXCLUSION,
        /** 과실 항목(직원-36). 찾은 것은 finding kind=BEHAVIOR */
        FAULT
    }

    public enum Scope {
        /** 사건 하나에 한 번 판정(시트의 사고 전 과실, 링크 및 앱 관련 과실 등 과실 범위) */
        INCIDENT,
        /** 피해 거래마다 판정(시트 범위 "TX 거래") */
        TX
    }

    public enum JudgeBy {
        /** 코드가 데이터 조건으로 해당과 비해당을 가름 */
        DATA,
        /** 근거 검증을 통과한 서류 태그가 있으면 해당 */
        DOC,
        /** 거래의 기록과 서류를 함께 봄(EX_SELF_ORDER) */
        DOC_RECORD,
        /** 담당자가 판단 */
        MANUAL
    }
}
