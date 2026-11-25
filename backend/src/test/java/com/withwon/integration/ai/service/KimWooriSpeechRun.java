package com.withwon.integration.ai.service;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.client.ClovaSpeechClient;
import com.withwon.integration.ai.client.ClovaSpeechParser;
import com.withwon.integration.ai.dto.Utterance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 진짜 CLOVA Speech로 김우리 통화 녹음(두 통화, 깨끗한 음질과 전화 음질)을 읽어 정답 대본과 견준다.
 * 실행: backend/.env에 SPEECH_API_URL, SPEECH_API_KEY를 채우고 ./gradlew clovaTest --tests '*KimWooriSpeechRun'
 * (--tests 없이 돌리면 OCR 시험도 같이 돈다). 한 번에 호출 4번, 녹음 약 4.8분을 쓴다(Free는 월 20분).
 * 결과: build/speech-kim-woori/<녹음>.txt(화자, 시작, 끝, 글)와 summary.txt.
 */
@Tag("clova")
class KimWooriSpeechRun {

    private static final Path OUT = Path.of("build/speech-kim-woori");
    private static final Pattern SCRIPT_LINE = Pattern.compile("\\[(\\d+):(\\d+\\.\\d+)] (.+?): (.+)");

    /** 정답 대본 한 줄. end는 다음 줄 시작(마지막 줄은 통화 길이). */
    private record Expected(String role, int startMs, int endMs, String text) {
    }

    @Test
    void 김우리_통화_녹음을_모두_읽는다() throws Exception {
        assumeTrue(Files.isDirectory(TestFiles.KIM_WOORI), "김우리 시험 서류가 없어 건너뜀");
        Properties env = TestFiles.env();
        var service = new SpeechReadService(
                new ClovaSpeechClient(env.getProperty("SPEECH_API_URL", ""), env.getProperty("SPEECH_API_KEY", "")),
                new ClovaSpeechParser(new JsonMapper()));
        Files.createDirectories(OUT);

        var summary = new ArrayList<String>();
        for (JsonNode call : TestFiles.kimWooriAnswer().path("통화녹음_정답").path("통화")) {
            List<Expected> script = script(call);
            for (String suffix : List.of("_깨끗한음질.mp3", "_전화음질.m4a")) {
                String fileName = call.path("파일").asString() + suffix;
                List<Utterance> utterances = service.read(TestFiles.kimWoori(fileName), fileName);
                Files.write(OUT.resolve(fileName + ".txt"), utterances.stream()
                        .map(u -> "%s\t%d\t%d\t%s".formatted(u.speaker(), u.startMs(), u.endMs(), u.text())).toList());

                summary.add("%s: 발화 %d개(대본 %d줄), 글자 오류율 %.1f%%, 화자별 겹친 시간(ms) %s".formatted(
                        fileName, utterances.size(), script.size(), 100 * charErrorRate(script, utterances),
                        overlapBySpeaker(script, utterances)));
                assertThat(utterances).as(fileName).isNotEmpty();
            }
        }
        Files.write(OUT.resolve("summary.txt"), summary);
        summary.forEach(System.out::println);
    }

    private static List<Expected> script(JsonNode call) {
        var starts = new ArrayList<Matcher>();
        for (JsonNode line : call.path("대본")) {
            Matcher m = SCRIPT_LINE.matcher(line.asString());
            assertThat(m.matches()).as(line.asString()).isTrue();
            starts.add(m);
        }
        var script = new ArrayList<Expected>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? startMs(starts.get(i + 1)) : (int) (call.path("길이_초").asDouble() * 1000);
            script.add(new Expected(starts.get(i).group(3), startMs(starts.get(i)), end, starts.get(i).group(4)));
        }
        return script;
    }

    private static int startMs(Matcher m) {
        return (int) Math.round((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000);
    }

    // 띄어쓰기와 문장부호를 뺀 글자끼리의 편집 거리 / 대본 글자 수. 대본은 숫자를 한글로 적어(삼백만 원) Speech가 300만 원으로 쓰면 오류로 잡힌다.
    private static double charErrorRate(List<Expected> script, List<Utterance> utterances) {
        String expected = lettersOnly(String.join("", script.stream().map(Expected::text).toList()));
        String actual = lettersOnly(String.join("", utterances.stream().map(Utterance::text).toList()));
        int[] row = new int[actual.length() + 1];
        for (int j = 0; j <= actual.length(); j++) {
            row[j] = j;
        }
        for (int i = 1; i <= expected.length(); i++) {
            int diagonal = row[0];
            row[0] = i;
            for (int j = 1; j <= actual.length(); j++) {
                int substitute = diagonal + (expected.charAt(i - 1) == actual.charAt(j - 1) ? 0 : 1);
                diagonal = row[j];
                row[j] = Math.min(substitute, Math.min(row[j], row[j - 1]) + 1);
            }
        }
        return (double) row[actual.length()] / expected.length();
    }

    private static String lettersOnly(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    // Speech가 붙인 화자(A, B)가 대본의 사기범, 피해자 구간과 각각 몇 ms 겹치는지. 화자 분리가 맞으면 화자마다 한 역할에 몰린다.
    private static Map<String, Map<String, Integer>> overlapBySpeaker(List<Expected> script, List<Utterance> utterances) {
        var overlap = new TreeMap<String, Map<String, Integer>>();
        for (Utterance u : utterances) {
            for (Expected e : script) {
                int ms = Math.min(u.endMs(), e.endMs()) - Math.max(u.startMs(), e.startMs());
                if (ms > 0) {
                    overlap.computeIfAbsent(u.speaker(), k -> new TreeMap<>()).merge(e.role(), ms, Integer::sum);
                }
            }
        }
        return overlap;
    }
}
