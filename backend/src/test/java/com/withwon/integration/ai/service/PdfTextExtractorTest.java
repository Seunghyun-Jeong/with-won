package com.withwon.integration.ai.service;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.dto.Bbox;
import com.withwon.integration.ai.dto.ReadLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void 글자_층이_있는_PDF는_쪽마다_줄을_뽑는다() throws IOException {
        List<ReadLine> lines = extractor.extract(TestFiles.pdf("Hello World", "Second page")).orElseThrow();

        assertThat(lines).extracting(ReadLine::page, ReadLine::text)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "Hello World"),
                        org.assertj.core.groups.Tuple.tuple(2, "Second page"));
    }

    @Test
    void bbox는_쪽_크기에_대한_비율이다() throws IOException {
        Bbox bbox = extractor.extract(TestFiles.pdf("Hello World")).orElseThrow().get(0).bbox();

        // 글은 왼쪽 72pt, 아래에서 700pt(위에서 92pt)에 썼고 쪽은 612x792pt다.
        assertThat(bbox.x()).isCloseTo(72.0 / 612, within(1e-3));
        assertThat(bbox.y() + bbox.h()).isCloseTo(92.0 / 792, within(5e-3));
        assertThat(bbox.w()).isBetween(0.05, 0.5);
    }

    @Test
    void 같은_높이라도_글자_크기보다_넓게_떨어진_글은_다른_줄이다() throws IOException {
        List<ReadLine> lines = extractor.extract(TestFiles.pdfRow("Name", "Kim Woori")).orElseThrow();

        assertThat(lines).extracting(ReadLine::text).containsExactly("Name", "Kim Woori");
    }

    @Test
    void 띄어쓰기로_끝난_칸도_넓게_떨어진_다음_칸과_다른_줄이다() throws IOException {
        List<ReadLine> lines = extractor.extract(TestFiles.pdfRow("Name ", "Kim Woori")).orElseThrow();

        assertThat(lines).extracting(ReadLine::text).containsExactly("Name", "Kim Woori");
    }

    @Test
    void 글자_층이_없는_쪽이_하나라도_있으면_전자_PDF가_아니다() throws IOException {
        assertThat(extractor.extract(TestFiles.pdf("Hello", ""))).isEmpty();
    }

    @Test
    void 김우리_전자_신청서에서_피해자_정보를_읽는다() throws IOException {
        JsonNode victim = TestFiles.kimWooriAnswer().path("피해자");

        String all = String.join("\n", extractor.extract(TestFiles.kimWoori("01_피해구제신청서_전자.pdf"))
                .orElseThrow().stream().map(ReadLine::text).toList());

        assertThat(all).contains(victim.path("name").asString(), victim.path("acct").asString(),
                victim.path("mobile").asString());
    }

    @Test
    void 김우리_전자_신청서는_표_칸마다_줄이_나뉜다() throws IOException {
        List<String> texts = extractor.extract(TestFiles.kimWoori("01_피해구제신청서_전자.pdf"))
                .orElseThrow().stream().map(ReadLine::text).toList();

        assertThat(texts).containsSubsequence("성 명", "김우리", "생년월일", "1990. 5. 12.")
                .contains("자산의 종류 및", "3,000,000원");
    }

    @Test
    void 김우리_스캔_신청서와_사실확인원은_전자_PDF가_아니다() throws IOException {
        assertThat(extractor.extract(TestFiles.kimWoori("01_피해구제신청서_스캔.pdf"))).isEmpty();
        assertThat(extractor.extract(TestFiles.kimWoori("02_사건사고사실확인원.pdf"))).isEmpty();
    }
}
