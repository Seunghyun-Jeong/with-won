package com.withwon.integration.ai.service;

import com.withwon.integration.ai.client.ClovaSpeechClient;
import com.withwon.integration.ai.client.ClovaSpeechParser;
import com.withwon.integration.ai.dto.Utterance;
import java.util.List;
import org.springframework.stereotype.Service;

/** 녹음 파일 하나를 CLOVA Speech로 읽어 발화 목록으로 돌려준다. */
@Service
public class SpeechReadService {

    private final ClovaSpeechClient speech;
    private final ClovaSpeechParser parser;

    public SpeechReadService(ClovaSpeechClient speech, ClovaSpeechParser parser) {
        this.speech = speech;
        this.parser = parser;
    }

    public List<Utterance> read(byte[] file, String fileName) {
        return parser.parse(speech.recognize(file, fileName));
    }
}
