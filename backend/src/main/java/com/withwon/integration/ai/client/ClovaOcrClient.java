package com.withwon.integration.ai.client;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * CLOVA OCR General(V2)에 파일 하나를 보내고 응답 JSON을 그대로 돌려준다. 해석은 ClovaOcrParser가 한다.
 * 원본 사진은 인식 엔진(CLOVA)까지만 가고 LLM에는 가지 않는다(CLAUDE.md 규칙).
 */
@Component
public class ClovaOcrClient {

    private final RestClient http = RestClient.create();
    private final String url;
    private final String secret;

    public ClovaOcrClient(@Value("${ocr.api.url}") String url, @Value("${ocr.api.key}") String secret) {
        this.url = url;
        this.secret = secret;
    }

    /** format: pdf, png, jpg. PDF는 한 번에 10쪽까지라 ReadService가 나눠서 보낸다. */
    public String recognize(byte[] data, String format) {
        // 키가 없어도 앱은 떠야 하므로(OCR을 안 쓰는 기능, 팀원 PC) 부를 때 막는다.
        if (url.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("backend/.env나 환경변수에 OCR_API_URL, OCR_API_KEY가 없어 OCR을 부를 수 없음");
        }
        Map<String, Object> body = Map.of(
                "version", "V2",
                "requestId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "lang", "ko",
                "images", List.of(Map.of(
                        "format", format,
                        "name", "document",
                        "data", Base64.getEncoder().encodeToString(data))));
        return http.post()
                .uri(url)
                .header("X-OCR-SECRET", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
