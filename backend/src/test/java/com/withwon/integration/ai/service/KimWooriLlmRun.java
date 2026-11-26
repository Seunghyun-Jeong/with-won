package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.client.ClaudeLlmClient;
import com.withwon.integration.ai.dto.Evidence;
import com.withwon.integration.ai.dto.EvidenceStatus;
import com.withwon.integration.ai.dto.LlmCall;
import com.withwon.integration.ai.dto.SummaryItem;
import com.withwon.integration.ai.dto.SummaryResult;
import com.withwon.integration.ai.dto.TimelineEvent;
import com.withwon.integration.ai.dto.TimelineResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// 진짜 Claude로 김우리 사건 타임라인과 요약을 만든다(돈이 드는 호출, ./gradlew llmTest로만. 하나만: --tests '*KimWooriLlmRun.김우리_사건_요약*').
// 두 작업 모두 같은 입력이다. 필수 서류(피해구제신청서, 확인원, 자율배상 신청서)와 선택 캡처를 넣는다. 신분증은 LLM에 보내지 않고, 같은 서류의 사진, 스캔본은 겹치므로 하나씩만 넣는다. 통화 녹취는 지금 넣지 않는다(KimWooriCase).
// 결과는 build/llm-timeline-kim-woori/, build/llm-summary-kim-woori/에 남기고, 토큰 수는 ClaudeLlmClient 로그로 본다.
@Tag("llm")
class KimWooriLlmRun {

    private static String key() throws IOException {
        String key = TestFiles.env().getProperty("ANTHROPIC_API_KEY");
        assumeTrue(key != null && !key.isBlank(), ".env에 ANTHROPIC_API_KEY가 없어 건너뜀");
        return key;
    }

    private static KimWooriCase kimWoori() throws IOException {
        return new KimWooriCase()
                .add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("사건사고사실확인원", "02_사건사고사실확인원.pdf.txt")
                .add("문자캡처", "05_문자캡처.png.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt")
                .addCompForm();
    }

    private static StringBuilder calls(String failReason, List<LlmCall> calls) {
        StringBuilder sb = new StringBuilder("실패: " + failReason + "\n");
        for (LlmCall c : calls) {
            sb.append("\n== 호출 ").append(c.provider()).append(' ').append(c.model()).append(' ').append(c.promptVersion())
                    .append("\n-- 보낸 글\n").append(c.input()).append("\n-- 답\n").append(c.output()).append('\n');
        }
        return sb;
    }

    private static void save(String dir, StringBuilder sb) throws IOException {
        Path out = Path.of("build", dir);
        Files.createDirectories(out);
        Files.writeString(out.resolve("result.txt"), sb.toString());
        System.out.println(sb);
    }

    private static void appendEvidence(StringBuilder sb, List<Evidence> evidence) {
        evidence.forEach(ev -> sb.append("\n    #").append(ev.fragmentId()).append(ev.verified() ? " O " : " X ").append(ev.quote()));
    }

    @Test
    void 김우리_사건_타임라인을_진짜_Claude로_만든다() throws Exception {
        KimWooriCase kim = kimWoori();

        TimelineResult result = new TimelineService(new ClaudeLlmClient(key())).build(kim.fragments, kim.labels, 2026);

        StringBuilder sb = calls(result.failReason(), result.calls());
        if (result.events() != null) {
            Map<EvidenceStatus, Long> counts = result.events().stream()
                    .collect(Collectors.groupingBy(TimelineEvent::status, Collectors.counting()));
            sb.append("\n== 항목 ").append(result.events().size()).append("개, 근거 상태 ").append(counts).append('\n');
            for (TimelineEvent e : result.events()) {
                sb.append("\n[").append(e.status()).append("] ").append(e.date()).append(' ').append(e.time())
                        .append(' ').append(e.displayWhat())
                        .append(e.unknownLabels().isEmpty() ? "" : " 모르는 이름표 " + e.unknownLabels());
                appendEvidence(sb, e.evidence());
            }
        }
        save("llm-timeline-kim-woori", sb);
        assertThat(result.failReason()).isNull();
    }

    @Test
    void 김우리_사건_요약을_진짜_Claude로_만든다() throws Exception {
        KimWooriCase kim = kimWoori();

        SummaryResult result = new SummaryService(new ClaudeLlmClient(key())).summarize(kim.fragments, kim.labels);

        StringBuilder sb = calls(result.failReason(), result.calls());
        if (result.sentences() != null) {
            for (String kind : List.of("요약", "확인 필요")) {
                List<SummaryItem> items = kind.equals("요약") ? result.sentences() : result.checks();
                Map<EvidenceStatus, Long> counts = items.stream()
                        .collect(Collectors.groupingBy(SummaryItem::status, Collectors.counting()));
                sb.append("\n== ").append(kind).append(' ').append(items.size()).append("개, 근거 상태 ").append(counts).append('\n');
                for (SummaryItem i : items) {
                    sb.append("\n[").append(i.status()).append("] ").append(i.displayText())
                            .append(i.unknownLabels().isEmpty() ? "" : " 모르는 이름표 " + i.unknownLabels())
                            .append(i.unverifiedAmounts().isEmpty() ? "" : " 근거에 없는 금액 " + i.unverifiedAmounts());
                    appendEvidence(sb, i.evidence());
                }
            }
        }
        save("llm-summary-kim-woori", sb);
        assertThat(result.failReason()).isNull();
    }
}
