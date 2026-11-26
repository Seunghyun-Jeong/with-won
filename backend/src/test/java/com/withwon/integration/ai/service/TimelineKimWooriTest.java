package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.TimelineEvent;
import com.withwon.integration.ai.dto.TimelineResult;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

// 김우리 사건을 가명처리한 글(KimWooriCase)로 가짜 LLM 답을 검증하고 실제 값으로 잇는다.
class TimelineKimWooriTest {

    private final KimWooriCase kim = new KimWooriCase();

    @Test
    void 김우리_사건을_가명처리한_글로_타임라인을_만들고_실제_값으로_잇는다() throws IOException {
        kim.add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("문자캡처", "05_문자캡처.png.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt");

        // 이체내역에서 박민수 행은 11.18 / 14:32 / [예금주1] / -3,000,000원 순서로 읽혔다.
        long holder = kim.idOf("이체내역캡처", "[예금주1]", 0);
        long call = kim.idContaining("피해구제신청서", "수사관이라는 사람에게서 전화를 받았습니다");
        long smsDate = kim.idOf("문자캡처", "2026년 11월 18일 (수)", 0);
        long smsBody = kim.idContaining("문자캡처", "[피해자]님 사건");
        long smsTime = kim.idOf("문자캡처", "오후 1:56", 0);
        String answer = """
                {"events": [
                 {"what": "[피해자]가 [예금주1]에게 3,000,000원을 이체함", "dateText": "11.18", "timeText": "14:32",
                  "evidence": [{"fragmentId": %d, "quote": "11.18"}, {"fragmentId": %d, "quote": "14:32"},
                               {"fragmentId": %d, "quote": "[예금주1]"}, {"fragmentId": %d, "quote": "-3,000,000원"}]},
                 {"what": "검찰 사칭 문자로 보안점검 앱 설치를 요구받음", "dateText": "2026년 11월 18일", "timeText": "오후 1:56",
                  "evidence": [{"fragmentId": %d, "quote": "2026년 11월 18일"}, {"fragmentId": %d, "quote": "[피해자]님 사건"},
                               {"fragmentId": %d, "quote": "오후 1:56"}]},
                 {"what": "검찰 수사관을 사칭한 사람이 [피해자]에게 전화함", "dateText": "2026년 11월 18일", "timeText": "오후 1시 50분경",
                  "evidence": [{"fragmentId": %d, "quote": "2026년 11월 18일 오후 1시 50분경 서울중앙지방검찰청 수사관이라는 사람에게서"}]}
                ]}""".formatted(holder - 2, holder - 1, holder, holder + 1, smsDate, smsBody, smsTime, call);
        FakeLlm llm = new FakeLlm(answer);

        TimelineResult result = new TimelineService(llm).build(kim.fragments, kim.labels, 2026);

        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "900512", "482901", "1002-384");
        assertThat(result.events()).allMatch(e -> e.status() == EvidenceStatus.VERIFIED);
        // LLM이 시각 순서를 틀리게 줘도 아는 시각으로 코드가 바로잡는다. 신청서의 "오후 1시 50분경"도 13:50으로 읽는다.
        assertThat(result.events()).extracting(TimelineEvent::displayWhat).containsExactly(
                "검찰 수사관을 사칭한 사람이 김우리에게 전화함",
                "검찰 사칭 문자로 보안점검 앱 설치를 요구받음",
                "김우리가 박민수에게 3,000,000원을 이체함");
        assertThat(result.events()).extracting(TimelineEvent::time)
                .containsExactly(LocalTime.of(13, 50), LocalTime.of(13, 56), LocalTime.of(14, 32));
        assertThat(result.events()).extracting(TimelineEvent::date).containsOnly(LocalDate.of(2026, 11, 18));
    }

    @Test
    void 자율배상_신청서의_경위와_사고인지_일시도_가명처리해_근거로_쓴다() throws IOException {
        kim.addCompForm();

        long noticed = kim.idContaining("자율배상신청서", "최초 사고인지 일시");
        long remote = kim.idContaining("자율배상신청서", "제가 하지 않은 950만원");
        String remoteText = kim.fragments.get((int) remote - 1).maskedText();
        String answer = """
                {"events": [
                 {"what": "[예금주2] 계좌로 피해자가 하지 않은 950만원 이체가 됨", "dateText": null, "timeText": "오후 3시 10분",
                  "evidence": [{"fragmentId": %d, "quote": "%s"}]},
                 {"what": "피해자가 피해를 처음 알아챔", "dateText": "2026-11-18", "timeText": "15:41",
                  "evidence": [{"fragmentId": %d, "quote": "2026-11-18 15:41"}]}
                ]}""".formatted(remote, remoteText, noticed);
        FakeLlm llm = new FakeLlm(answer);

        TimelineResult result = new TimelineService(llm).build(kim.fragments, kim.labels, 2026);

        // 피해자가 직접 쓴 글 속 계좌, 예금주, 휴대폰 번호도 서류와 같은 이름표로 바뀐다.
        assertThat(llm.inputs.getFirst()).doesNotContain("김우리", "박민수", "이정호", "482901", "110-472", "010-2847")
                .contains("[예금주1]", "[예금주2]");
        assertThat(result.events()).allMatch(e -> e.status() == EvidenceStatus.VERIFIED);
        assertThat(result.events()).extracting(TimelineEvent::displayWhat).containsExactly(
                "이정호 계좌로 피해자가 하지 않은 950만원 이체가 됨", "피해자가 피해를 처음 알아챔");
        assertThat(result.events()).extracting(TimelineEvent::time)
                .containsExactly(LocalTime.of(15, 10), LocalTime.of(15, 41));
    }
}
