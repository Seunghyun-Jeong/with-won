package com.withwon.integration.ai.read;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 테스트가 쓰는 파일: 즉석에서 만드는 PDF와 이미지, git에 없는 김우리 시험 서류. */
final class TestFiles {

    // 저장소에는 올리지 않는 시험 서류(.git/info/exclude). 없으면 이 서류를 쓰는 테스트는 건너뛴다.
    static final Path KIM_WOORI = Path.of("src/test/resources/ai/samples/김우리");

    private TestFiles() {
    }

    /** 쪽마다 글 한 줄을 쓴 PDF. 빈 문자열인 쪽은 글자 층이 없는 쪽(스캔본처럼)이 된다. */
    static byte[] pdf(String... pageTexts) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                var page = new PDPage();
                doc.addPage(page);
                if (text.isEmpty()) {
                    continue;
                }
                try (var content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] blankPdf(int pages) throws IOException {
        var texts = new String[pages];
        Arrays.fill(texts, "");
        return pdf(texts);
    }

    static byte[] image(String format, int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    static byte[] kimWoori(String fileName) throws IOException {
        Path file = KIM_WOORI.resolve(fileName);
        assumeTrue(Files.exists(file), "김우리 시험 서류가 없어 건너뜀: " + file);
        return Files.readAllBytes(file);
    }

    static JsonNode kimWooriAnswer() throws IOException {
        return new JsonMapper().readTree(kimWoori("정답.json"));
    }
}
