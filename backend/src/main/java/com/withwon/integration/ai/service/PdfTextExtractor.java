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

    /**
     * PDFTextStripper가 단어마다 부르는 writeString을 모아 두었다가 줄이 끝날 때 한 줄로 만든다.
     * PDFBox는 같은 높이의 글을 쪽 끝까지 한 줄로 묶어 표의 한 행이 통째로 한 줄이 되므로, 글자 사이가 넓게
     * 벌어진 곳(표의 칸 경계)에서도 끊는다. 그래야 CLOVA OCR처럼 칸마다 한 줄이 되어 스캔본과 같은 단위가 된다.
     * 간격은 단어 사이가 아니라 글자 사이마다 잰다. PDFBox는 띄어쓰기 글자 뒤에는 단어 구분을 넣지 않아서,
     * "자산의 종류 및 "처럼 띄어쓰기로 끝난 칸은 다음 칸과 한 단어로 넘어오기 때문이다. 그래서 줄의 글도
     * 넘겨받은 단어 대신 글자마다의 유니코드로 만든다. PDFBox가 단어에 해 주는 합자(ﬁ) 풀기와 오른쪽에서 왼쪽으로
     * 쓰는 글 정렬은 빠지지만, 한국어 은행 서류에는 나오지 않는다.
     */
    private static class LineStripper extends PDFTextStripper {

        // 글자 크기의 몇 배보다 넓으면 다른 칸으로 본다. 김우리 전자 신청서에서 칸 안에서 글자 사이는 가장 넓은 곳이 0.36배,
        // 칸 사이는 가장 좁은 곳이 1.75배였다(11-25 측정).
        private static final double CELL_GAP_EM = 1.0;

        private final List<ReadLine> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        // 띄어쓰기 글자는 담지 않는다. 간격을 마지막 보이는 글자에서 재고, bbox가 칸 밖으로 늘지 않게.
        private final List<TextPosition> positions = new ArrayList<>();

        LineStripper() {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String word, List<TextPosition> textPositions) {
            for (TextPosition p : textPositions) {
                String ch = p.getUnicode();
                if (ch.isBlank()) {
                    text.append(' ');
                    continue;
                }
                if (!positions.isEmpty()) {
                    TextPosition prev = positions.getLast();
                    double gap = p.getXDirAdj() - (prev.getXDirAdj() + prev.getWidthDirAdj());
                    if (gap > prev.getFontSizeInPt() * CELL_GAP_EM) {
                        flushLine();
                    }
                }
                text.append(ch);
                positions.add(p);
            }
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
