package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.ClovaOcrClient;
import com.withwon.integration.ai.client.ClovaOcrParser;
import com.withwon.integration.ai.dto.ReadLine;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

/**
 * 서류 파일 하나를 줄(fragment LINE) 목록으로 읽는다.
 * 전자 PDF는 PDFBox로 바로, 스캔 PDF와 사진은 CLOVA OCR로 읽는다.
 * 종류는 파일 이름이 아니라 앞 몇 바이트로 가린다. 피해자가 올린 파일 이름과 확장자는 틀릴 수 있다.
 */
@Service
public class ReadService {

    // CLOVA OCR General은 PDF를 호출 한 번에 10쪽까지 읽는다(공식 문서, 2026-11-23 확인).
    static final int OCR_PDF_PAGES_PER_CALL = 10;

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final PdfTextExtractor pdfText;
    private final ClovaOcrClient ocr;
    private final ClovaOcrParser parser;

    public ReadService(PdfTextExtractor pdfText, ClovaOcrClient ocr, ClovaOcrParser parser) {
        this.pdfText = pdfText;
        this.ocr = ocr;
        this.parser = parser;
    }

    public List<ReadLine> read(byte[] file) {
        if (startsWith(file, PDF)) {
            return pdfText.extract(file).orElseGet(() -> ocrPdf(file));
        }
        if (startsWith(file, PNG)) {
            return ocrImage(file, "png");
        }
        if (startsWith(file, JPG)) {
            return ocrImage(file, "jpg");
        }
        throw new IllegalArgumentException("PDF, PNG, JPG만 읽을 수 있음");
    }

    private List<ReadLine> ocrPdf(byte[] file) {
        try (var doc = Loader.loadPDF(file)) {
            var splitter = new Splitter();
            splitter.setSplitAtPage(OCR_PDF_PAGES_PER_CALL);
            var lines = new ArrayList<ReadLine>();
            int firstPage = 1;
            for (PDDocument chunk : splitter.split(doc)) {
                try (chunk; var out = new ByteArrayOutputStream()) {
                    chunk.save(out);
                    lines.addAll(parser.parsePdf(ocr.recognize(out.toByteArray(), "pdf"), firstPage));
                    firstPage += chunk.getNumberOfPages();
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("PDF를 나누지 못함", e);
        }
    }

    // 이미지 응답에는 크기가 없고 좌표가 원본 픽셀이라, bbox 비율을 내려고 원본 크기를 읽는다.
    private List<ReadLine> ocrImage(byte[] file, String format) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
            return parser.parseImage(ocr.recognize(file, format), image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 읽지 못함", e);
        }
    }

    private static boolean startsWith(byte[] file, byte[] magic) {
        return file.length >= magic.length && Arrays.equals(file, 0, magic.length, magic, 0, magic.length);
    }
}
