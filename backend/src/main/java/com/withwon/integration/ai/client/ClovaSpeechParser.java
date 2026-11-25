package com.withwon.integration.ai.client;

import com.withwon.integration.ai.dto.Utterance;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CLOVA Speech 장문 인식(sync) 응답을 발화(Utterance)로 바꾼다.
 * 응답은 화자가 이어 말한 구간(segment)마다 start, end(ms), text와 화자(speaker.name이 A, B …)를 준다.
 */
@Component
public class ClovaSpeechParser {

    private final JsonMapper json;

    public ClovaSpeechParser(JsonMapper json) {
        this.json = json;
    }

    public List<Utterance> parse(String response) {
        JsonNode root = json.readTree(response);
        String result = root.path("result").asString();
        if (!"COMPLETED".equals(result)) {
            throw new IllegalStateException("CLOVA Speech 인식 실패: " + result + " " + root.path("message").asString());
        }
        var utterances = new ArrayList<Utterance>();
        for (JsonNode segment : root.path("segments")) {
            utterances.add(new Utterance(
                    segment.path("speaker").path("name").asString(),
                    segment.path("start").asInt(),
                    segment.path("end").asInt(),
                    segment.path("text").asString()));
        }
        return utterances;
    }
}
