package com.withwon.integration.ai.service;

import static com.withwon.integration.ai.TestFiles.KIM_WOORI;
import static com.withwon.integration.ai.TestFiles.KIM_WOORI_KNOWN;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.MaskInput;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 김우리 서류를 진짜 CLOVA로 읽은 결과(clovaTest가 만든 build/ocr-kim-woori)를 실제 흐름대로 가명처리해 LLM 입력 조각으로 만든다.
 * 결과 파일이 없으면(clean 뒤 등) 그 시험은 건너뛴다.
 * 통화 녹취(build/speech-kim-woori)는 11-26부터 넣지 않는다. 진짜 통화 녹음을 구하기 어려워 합성음으로만 확인한 상태라,
 * 개발 시간이 남으면 다시 넣는다(CLAUDE.md 남은 일).
 */
final class KimWooriCase {

    static final Path OCR = Path.of("build/ocr-kim-woori");

    final List<EvidenceFragment> fragments = new ArrayList<>();
    List<MaskLabel> labels = List.of();

    /** 서류 하나를 조각으로 나눠 가리고, 대응표를 이어 받는다(보완 서류처럼 서류마다 maskForLlm). */
    KimWooriCase add(String document, String fileName) throws IOException {
        Path file = OCR.resolve(fileName);
        assumeTrue(Files.exists(file), "김우리 읽기 결과가 없어 건너뜀: " + file);
        return addTexts(document, Files.readAllLines(file).stream().filter(l -> !l.isBlank())
                .map(l -> l.substring(l.lastIndexOf('\t') + 1)).toList());
    }

    /**
     * 앱에서 쓴 자율배상 신청서(make_kim_woori.py가 만든 08_자율배상신청서.json)의 최초 사고인지 일시, 경위, 참고사항을 조각으로 넣는다.
     * ERD fragment.comp_form_id처럼 글 칸만 조각이 되고 문항 답은 점검 항목용이라 넣지 않는다.
     * 서류 줄과 같은 크기로 근거를 집도록 문장마다 한 조각으로 나눈다.
     */
    KimWooriCase addCompForm() throws IOException {
        Path file = KIM_WOORI.resolve("08_자율배상신청서.json");
        assumeTrue(Files.exists(file), "김우리 자율배상 신청서가 없어 건너뜀: " + file);
        JsonNode form = JsonMapper.shared().readTree(file.toFile());
        Stream<String> noticed = Stream.of("최초 사고인지 일시 " + form.get("noticed_at").asString().replace('T', ' '));
        Stream<String> sentences = Stream.of("narrative", "extra_note")
                .flatMap(k -> Stream.of(form.get(k).asString().split("(?<=[.?!])\\s+")));
        return addTexts("자율배상신청서", Stream.concat(noticed, sentences).toList());
    }

    private KimWooriCase addTexts(String document, List<String> texts) {
        MaskResult masked = new MaskService().maskForLlm(texts.stream().map(t -> new MaskInput(null, t)).toList(),
                KIM_WOORI_KNOWN, labels);
        labels = masked.labels();
        masked.texts().forEach(t -> fragments.add(new EvidenceFragment(fragments.size() + 1L, document, null, null, t)));
        return this;
    }
}
