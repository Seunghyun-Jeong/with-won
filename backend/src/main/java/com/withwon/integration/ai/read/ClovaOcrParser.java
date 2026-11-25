package com.withwon.integration.ai.read;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CLOVA OCR General V2 응답을 줄(ReadLine)로 바꾼다.
 * V2는 단어 묶음(field)마다 lineBreak를 주고, true인 field에서 줄이 끝난다.
 * PDF는 쪽마다 images 원소가 하나씩 오고 convertedImageInfo에 쪽 번호(pageIndex)와 변환된 이미지 크기가 있다.
 * 이미지는 convertedImageInfo가 없고 좌표가 원본 픽셀이라 원본 크기를 받아 비율을 낸다.
 */
@Component
public class ClovaOcrParser {

    private final JsonMapper json;

    public ClovaOcrParser(JsonMapper json) {
        this.json = json;
    }

    /** firstPage: 이 응답의 pageIndex 0이 원본 PDF의 몇 쪽인지(10쪽씩 나눠 보내므로 1, 11, 21 …). */
    public List<ReadLine> parsePdf(String response, int firstPage) {
        var lines = new ArrayList<ReadLine>();
        for (JsonNode image : successfulImages(response)) {
            JsonNode info = image.path("convertedImageInfo");
            lines.addAll(toLines(image, firstPage + info.path("pageIndex").asInt(),
                    info.path("width").asDouble(), info.path("height").asDouble()));
        }
        return lines;
    }

    public List<ReadLine> parseImage(String response, int width, int height) {
        var lines = new ArrayList<ReadLine>();
        for (JsonNode image : successfulImages(response)) {
            lines.addAll(toLines(image, 1, width, height));
        }
        return lines;
    }

    private List<JsonNode> successfulImages(String response) {
        List<JsonNode> images = json.readTree(response).path("images").valueStream().toList();
        for (JsonNode image : images) {
            String result = image.path("inferResult").asString();
            if (!"SUCCESS".equals(result)) {
                throw new IllegalStateException("CLOVA OCR 인식 실패: " + result + " " + image.path("message").asString());
            }
        }
        return images;
    }

    private List<ReadLine> toLines(JsonNode image, int page, double pageWidth, double pageHeight) {
        var lines = new ArrayList<ReadLine>();
        var words = new ArrayList<JsonNode>();
        for (JsonNode field : image.path("fields")) {
            words.add(field);
            if (field.path("lineBreak").asBoolean()) {
                lines.add(toLine(words, page, pageWidth, pageHeight));
                words.clear();
            }
        }
        if (!words.isEmpty()) {
            lines.add(toLine(words, page, pageWidth, pageHeight));
        }
        return lines;
    }

    private ReadLine toLine(List<JsonNode> words, int page, double pageWidth, double pageHeight) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        var text = new StringBuilder();
        for (JsonNode word : words) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(word.path("inferText").asString());
            for (JsonNode v : word.path("boundingPoly").path("vertices")) {
                minX = Math.min(minX, v.path("x").asDouble());
                minY = Math.min(minY, v.path("y").asDouble());
                maxX = Math.max(maxX, v.path("x").asDouble());
                maxY = Math.max(maxY, v.path("y").asDouble());
            }
        }
        return new ReadLine(page, Bbox.ofCorners(minX, minY, maxX, maxY, pageWidth, pageHeight), text.toString());
    }
}
