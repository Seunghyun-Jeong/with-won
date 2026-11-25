package com.withwon.integration.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * CLOVA Speech 장문 인식(로컬 파일, POST {Invoke URL}/recognizer/upload)에 녹음 하나를 보내고 응답 JSON을 그대로 돌려준다.
 * 해석은 ClovaSpeechParser가 한다. 원본 녹음은 인식 엔진(CLOVA)까지만 가고 LLM에는 가지 않는다(CLAUDE.md 규칙).
 */
@Component
public class ClovaSpeechClient {

    // sync: 응답에 결과가 바로 온다. async는 받을 Callback URL이나 Object Storage가 있어야 해서 쓰지 않는다.
    // 발화 구간만 쓰므로 단어별 시각(wordAlignment)과 전체 글(fullText)은 받지 않는다. 화자 분리는 기본값이 켜짐이지만 꼭 필요해서 적어 둔다.
    private static final String PARAMS = """
            {"language":"ko-KR","completion":"sync","wordAlignment":false,"fullText":false,"diarization":{"enable":true}}""";

    private final RestClient http = RestClient.create();
    private final String url;
    private final String secret;

    public ClovaSpeechClient(@Value("${speech.api.url}") String url, @Value("${speech.api.key}") String secret) {
        this.url = url;
        this.secret = secret;
    }

    /** fileName: 확장자가 붙은 이름. 지원 형식은 mp3, aac, ac3, ogg, flac, wav, m4a(공식 문서, 2026-11-25 확인). */
    public String recognize(byte[] data, String fileName) {
        // 키가 없어도 앱은 떠야 하므로(녹음을 안 쓰는 기능, 팀원 PC) 부를 때 막는다.
        if (url.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("backend/.env나 환경변수에 SPEECH_API_URL, SPEECH_API_KEY가 없어 녹음을 읽을 수 없음");
        }
        // MultipartBodyBuilder는 reactive-streams가 있어야 해서(webmvc에는 없음) 칸을 직접 만든다.
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("media", new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        var json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        parts.add("params", new HttpEntity<>(PARAMS, json));
        return http.post()
                .uri(url + "/recognizer/upload")
                .header("X-CLOVASPEECH-API-KEY", secret)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);
    }
}
