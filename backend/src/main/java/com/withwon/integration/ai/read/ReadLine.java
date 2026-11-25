package com.withwon.integration.ai.read;

/**
 * 서류에서 읽은 한 줄. fragment 표의 kind=LINE 한 행이 되고, seq는 ReadService가 돌려준 목록 순서(1부터)다.
 * page는 1부터 센다.
 */
public record ReadLine(int page, Bbox bbox, String text) {
}
