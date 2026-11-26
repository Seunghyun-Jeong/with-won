package com.withwon.integration.ai.dto;

/**
 * 원본에서 줄을 강조할 위치(직원-6, 직원-17). 왼쪽 위가 (0, 0)이고 값은 쪽 너비, 높이에 대한 비율(0~1)이다.
 * PDFBox는 pt, CLOVA는 픽셀로 단위가 달라서 비율로 맞췄다. 화면은 원본을 어떤 크기로 그리든 곱하기만 하면 된다.
 */
public record Bbox(double x, double y, double w, double h) {

    public static Bbox ofCorners(double minX, double minY, double maxX, double maxY, double pageWidth, double pageHeight) {
        return new Bbox(minX / pageWidth, minY / pageHeight, (maxX - minX) / pageWidth, (maxY - minY) / pageHeight);
    }
}
