package com.withwon.integration.ai.client;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.dto.Bbox;
import com.withwon.integration.ai.dto.ReadLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// 응답 예시는 NAVER Cloud 공식 문서 General OCR V2 응답 예시 그대로다.
// 예시 좌표가 convertedImageInfo 크기(1224x1584)보다 커서 비율이 1을 넘지만, 문서 예시라 그대로 둔다.
class ClovaOcrParserTest {

    private final ClovaOcrParser parser = new ClovaOcrParser(new JsonMapper());

    @Test
    void lineBreak가_true인_칸에서_줄이_끝나고_마지막_칸들은_한_줄이_된다() throws IOException {
        List<ReadLine> lines = parser.parsePdf(TestFiles.officialClovaExample(), 1);

        assertThat(lines).extracting(ReadLine::text).containsExactly("How beautiful", "this world is");
    }

    @Test
    void PDF는_pageIndex에_시작_쪽을_더해_쪽_번호를_매긴다() throws IOException {
        List<ReadLine> lines = parser.parsePdf(TestFiles.officialClovaExample(), 11);

        assertThat(lines).extracting(ReadLine::page).containsOnly(11);
    }

    @Test
    void PDF_bbox는_변환된_이미지_크기에_대한_비율이고_줄에_속한_칸을_모두_감싼다() throws IOException {
        Bbox second = parser.parsePdf(TestFiles.officialClovaExample(), 1).get(1).bbox();

        assertThat(second.x()).isCloseTo(2314.6516 / 1224, within(1e-9));
        assertThat(second.y()).isCloseTo(1328.9293 / 1584, within(1e-9));
        assertThat(second.w()).isCloseTo((2430.3843 - 2314.6516) / 1224, within(1e-9));
        assertThat(second.h()).isCloseTo((1604.2931 - 1328.9293) / 1584, within(1e-9));
    }

    @Test
    void 이미지는_넘겨받은_원본_크기로_비율을_내고_1쪽이다() throws IOException {
        List<ReadLine> lines = parser.parseImage(TestFiles.officialClovaExample(), 3000, 2000);

        assertThat(lines).extracting(ReadLine::page).containsOnly(1);
        assertThat(lines.get(0).bbox().x()).isCloseTo(2713.7295 / 3000, within(1e-9));
    }

    @Test
    void 인식에_실패하면_예외를_던진다() throws IOException {
        String failed = TestFiles.officialClovaExample().replace("\"inferResult\": \"SUCCESS\"", "\"inferResult\": \"FAILURE\"");

        assertThatThrownBy(() -> parser.parsePdf(failed, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILURE");
    }
}
