package com.withwon.integration.ai.service;

import com.withwon.integration.ai.dto.Bbox;
import com.withwon.integration.ai.dto.ReadLine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * 전자 PDF의 글자 층을 줄 단위로 바로 뽑는다. OCR을 거치지 않으니 오인식이 없고 비용도 들지 않는다.
 * 글자 층이 없는 쪽이 하나라도 있으면 스캔본으로 보고 빈 값을 돌려준다(그 PDF는 통째로 OCR로 간다).
 * 쪽마다 나눠 PDFBox와 OCR을 섞지 않은 건, 섞인 PDF가 드물고 쪽 단위로 나누면 OCR 호출 수만 늘기 때문이다.
 */
@Component
public class PdfTextExtractor {

    public Optional<List<ReadLine>> extract(byte[] pdf) {
        try (var doc = Loader.loadPDF(pdf)) {
            var stripper = new LineStripper();
            stripper.getText(doc);
            long pagesWithText = stripper.lines.stream().mapToInt(ReadLine::page).distinct().count();
            return pagesWithText == doc.getNumberOfPages() ? Optional.of(stripper.lines) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF를 읽지 못함", e);
        }
    }

    /** PDFTextStripper가 단어마다 부르는 writeString을 모아 두었다가 줄이 끝날 때 한 줄로 만든다. */
    private static class LineStripper extends PDFTextStripper {

        private final List<ReadLine> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private final List<TextPosition> positions = new ArrayList<>();

        LineStripper() {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String word, List<TextPosition> textPositions) {
            text.append(word);
            positions.addAll(textPositions);
        }

        @Override
        protected void writeWordSeparator() {
            text.append(' ');
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        // 쪽의 마지막 줄 뒤에는 줄 구분이 오지 않는다.
        @Override
        protected void endPage(PDPage page) {
            flushLine();
        }

        private void flushLine() {
            String line = text.toString().strip();
            if (!line.isEmpty()) {
                lines.add(new ReadLine(getCurrentPageNo(), bboxOf(positions), line));
            }
            text.setLength(0);
            positions.clear();
        }

        // YDirAdj는 위에서 잰 글자 바닥선이라 글자 높이만큼 빼야 윗변이 된다.
        private static Bbox bboxOf(List<TextPosition> positions) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (TextPosition p : positions) {
                minX = Math.min(minX, p.getXDirAdj());
                maxX = Math.max(maxX, p.getXDirAdj() + p.getWidthDirAdj());
                minY = Math.min(minY, p.getYDirAdj() - p.getHeightDir());
                maxY = Math.max(maxY, p.getYDirAdj());
            }
            TextPosition first = positions.getFirst();
            return Bbox.ofCorners(minX, minY, maxX, maxY, first.getPageWidth(), first.getPageHeight());
        }
    }
}
