package com.withwon.integration.ai.service;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.client.ClovaOcrClient;
import com.withwon.integration.ai.client.ClovaOcrParser;
import com.withwon.integration.ai.dto.Bbox;
import com.withwon.integration.ai.dto.ReadLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 진짜 CLOVA OCR로 김우리 서류(PDF, 사진)를 모두 읽어 본다.
 * 실행: backend/.env에 OCR_API_URL, OCR_API_KEY를 채우고 ./gradlew clovaTest (./gradlew test에서는 clova 태그라 빠진다)
 * 결과: build/ocr-kim-woori/<서류>.txt(쪽, bbox, 글)와 summary.txt(정답 값을 찾았는지).
 */
@Tag("clova")
class KimWooriOcrRun {

    private static final Path OUT = Path.of("build/ocr-kim-woori");

    @Test
    void 김우리_서류를_모두_읽는다() throws Exception {
        assumeTrue(Files.isDirectory(TestFiles.KIM_WOORI), "김우리 시험 서류가 없어 건너뜀");
        Properties env = TestFiles.env();
        var service = new ReadService(new PdfTextExtractor(),
                new ClovaOcrClient(env.getProperty("OCR_API_URL", ""), env.getProperty("OCR_API_KEY", "")),
                new ClovaOcrParser(new JsonMapper()));
        Map<String, String> expected = expectedValues(TestFiles.kimWooriAnswer());
        Files.createDirectories(OUT);

        var summary = new ArrayList<String>();
        for (Path file : documents()) {
            List<ReadLine> lines = service.read(Files.readAllBytes(file));
            String all = String.join("\n", lines.stream().map(ReadLine::text).toList());
            Files.write(OUT.resolve(file.getFileName() + ".txt"), lines.stream().map(KimWooriOcrRun::format).toList());

            var found = expected.entrySet().stream().filter(e -> all.contains(e.getValue())).map(Map.Entry::getKey).toList();
            summary.add(file.getFileName() + ": " + lines.size() + "줄, 찾은 정답 " + found);
            assertThat(lines).as(file.getFileName().toString()).isNotEmpty();
            Thread.sleep(1000); // CLOVA OCR 권장 호출 속도 초당 1건
        }
        Files.write(OUT.resolve("summary.txt"), summary);
        summary.forEach(System.out::println);
    }

    private static List<Path> documents() throws IOException {
        try (Stream<Path> files = Files.list(TestFiles.KIM_WOORI)) {
            return files.filter(p -> p.toString().matches(".*\\.(pdf|png|jpg)$")).sorted().toList();
        }
    }

    // 서류에 그대로 찍혀 있을 만한 값만 고른다. 정답.json이 주인이고 여기서는 열쇠 이름만 고른다.
    private static Map<String, String> expectedValues(JsonNode answer) {
        var values = new LinkedHashMap<String, String>();
        JsonNode victim = answer.path("피해자");
        for (String key : List.of("name", "mobile", "acct")) {
            values.put("피해자." + key, victim.path(key).asString());
        }
        JsonNode frauds = answer.path("사기거래");
        for (int i = 0; i < frauds.size(); i++) {
            values.put("사기거래" + (i + 1) + ".acct", frauds.get(i).path("acct").asString());
        }
        values.put("사건번호", answer.path("사건사고사실확인원").path("사건번호").asString());
        return values;
    }

    private static String format(ReadLine line) {
        Bbox b = line.bbox();
        return "%d\t%.3f,%.3f,%.3f,%.3f\t%s".formatted(line.page(), b.x(), b.y(), b.w(), b.h(), line.text());
    }
}
