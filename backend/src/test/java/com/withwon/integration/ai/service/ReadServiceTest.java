package com.withwon.integration.ai.service;

import com.withwon.integration.ai.TestFiles;
import com.withwon.integration.ai.client.ClovaOcrClient;
import com.withwon.integration.ai.client.ClovaOcrParser;
import com.withwon.integration.ai.dto.ReadLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

// CLOVA 호출만 가짜로 바꾸고, PDF 판별과 응답 해석은 진짜 코드로 돈다.
class ReadServiceTest {

    private final ClovaOcrClient ocr = mock(ClovaOcrClient.class);
    private final ReadService service =
            new ReadService(new PdfTextExtractor(), ocr, new ClovaOcrParser(new JsonMapper()));

    @BeforeEach
    void ocrAnswersWithOfficialExample() throws IOException {
        when(ocr.recognize(any(), anyString())).thenReturn(TestFiles.officialClovaExample());
    }

    @Test
    void 전자_PDF는_OCR을_부르지_않고_PDFBox로_읽는다() throws IOException {
        List<ReadLine> lines = service.read(TestFiles.pdf("Hello World"));

        assertThat(lines).extracting(ReadLine::text).containsExactly("Hello World");
        verify(ocr, never()).recognize(any(), anyString());
    }

    @Test
    void 스캔_PDF는_OCR로_보내_줄로_바꾼다() throws IOException {
        List<ReadLine> lines = service.read(TestFiles.blankPdf(1));

        verify(ocr).recognize(any(), org.mockito.ArgumentMatchers.eq("pdf"));
        assertThat(lines).extracting(ReadLine::text).containsExactly("How beautiful", "this world is");
    }

    @Test
    void 열_쪽이_넘는_스캔_PDF는_열_쪽씩_나눠_보내고_쪽_번호를_이어_매긴다() throws IOException {
        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);

        List<ReadLine> lines = service.read(TestFiles.blankPdf(12));

        verify(ocr, times(2)).recognize(sent.capture(), anyString());
        assertThat(sent.getAllValues()).extracting(ReadServiceTest::pageCount).containsExactly(10, 2);
        // 가짜 응답은 매번 pageIndex 0이므로 두 번째 묶음의 줄은 11쪽이 된다.
        assertThat(lines).extracting(ReadLine::page).containsExactly(1, 1, 11, 11);
    }

    @Test
    void PNG는_png로_보내고_원본_크기로_비율을_낸다() throws IOException {
        List<ReadLine> lines = service.read(TestFiles.image("png", 3000, 2000));

        verify(ocr).recognize(any(), org.mockito.ArgumentMatchers.eq("png"));
        assertThat(lines.get(0).bbox().x()).isCloseTo(2713.7295 / 3000, within(1e-9));
    }

    @Test
    void JPG는_jpg로_보낸다() throws IOException {
        service.read(TestFiles.image("jpg", 100, 100));

        verify(ocr).recognize(any(), org.mockito.ArgumentMatchers.eq("jpg"));
    }

    @Test
    void PDF도_이미지도_아니면_거절한다() {
        assertThatThrownBy(() -> service.read("카톡 대화".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 김우리_스캔_신청서는_OCR로_보낸다() throws IOException {
        service.read(TestFiles.kimWoori("01_피해구제신청서_스캔.pdf"));

        verify(ocr).recognize(any(), org.mockito.ArgumentMatchers.eq("pdf"));
    }

    private static int pageCount(byte[] pdf) {
        try (var doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
