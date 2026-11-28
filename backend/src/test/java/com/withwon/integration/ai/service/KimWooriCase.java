package com.withwon.integration.ai.service;

import static com.withwon.integration.ai.TestFiles.KIM_WOORI;
import static com.withwon.integration.ai.TestFiles.KIM_WOORI_KNOWN;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.withwon.integration.ai.dto.EvidenceFragment;
import com.withwon.integration.ai.dto.MaskInput;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskResult;
import com.withwon.integration.ai.dto.TagResult;
import com.withwon.integration.ai.TestFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 김우리 서류를 진짜 CLOVA로 읽은 결과(clovaTest가 만든 build/ocr-kim-woori)를 실제 흐름대로 가명처리해 LLM 입력 조각으로 만든다.
 * 결과 파일이 없으면(clean 뒤 등) 그 시험은 건너뛴다.
 * 통화 녹취(build/speech-kim-woori)는 11-26부터 넣지 않는다. 진짜 통화 녹음을 구하기 어려워 합성음으로만 확인한 상태라,
 * 개발 시간이 남으면 다시 넣는다(CLAUDE.md 남은 일).
 */
public final class KimWooriCase {

    static final Path OCR = Path.of("build/ocr-kim-woori");

    final List<EvidenceFragment> fragments = new ArrayList<>();
    public List<MaskLabel> labels = List.of();
    /** tagRecorded가 쓴 가짜 LLM. 보낸 글을 시험이 본다. */
    FakeLlm llm;

    /** 서류 하나를 조각으로 나눠 가리고, 대응표를 이어 받는다(보완 서류처럼 서류마다 maskForLlm). */
    KimWooriCase add(String document, String fileName) throws IOException {
        Path file = OCR.resolve(fileName);
        assumeTrue(Files.exists(file), "김우리 읽기 결과가 없어 건너뜀: " + file);
        return addTexts(document, Files.readAllLines(file).stream().filter(l -> !l.isBlank())
                .map(l -> l.substring(l.lastIndexOf('\t') + 1)).toList());
    }

    /**
     * 앱에서 쓴 자율배상 신청서(make_kim_woori.py가 만든 08_자율배상신청서.json)의 최초 사고인지 일시, 경위, 참고사항을 조각으로 넣는다.
     * ERD fragment.comp_form_id처럼 글 칸만 조각이 되고 문항 답은 점검 항목용이라 넣지 않는다.
     * 서류 줄과 같은 크기로 근거를 집도록 문장마다 한 조각으로 나눈다.
     */
    KimWooriCase addCompForm() throws IOException {
        Path file = KIM_WOORI.resolve("08_자율배상신청서.json");
        assumeTrue(Files.exists(file), "김우리 자율배상 신청서가 없어 건너뜀: " + file);
        JsonNode form = JsonMapper.shared().readTree(file.toFile());
        Stream<String> noticed = Stream.of("최초 사고인지 일시 " + form.get("noticed_at").asString().replace('T', ' '));
        Stream<String> sentences = Stream.of("narrative", "extra_note")
                .flatMap(k -> Stream.of(form.get(k).asString().split("(?<=[.?!])\\s+")));
        return addTexts("자율배상신청서", Stream.concat(noticed, sentences).toList());
    }

    /**
     * 태깅 시험이 같이 쓰는 녹화 답: 필수 서류 4개와 신청서 글 칸을 넣고, 11-26 진짜 Claude 호출이 찾은 태그 5개(링크, 앱 설치, 주민번호 뒷자리,
     * OTP, 300만원 직접 이체. 950만원은 직접 이체로 안 붙임)를 가짜 LLM으로 돌려주어 TaggingService를 돌린다.
     */
    public TagResult tagRecorded() throws IOException {
        add("피해구제신청서", "01_피해구제신청서_전자.pdf.txt")
                .add("사건사고사실확인원", "02_사건사고사실확인원.pdf.txt")
                .add("문자캡처", "05_문자캡처.png.txt")
                .add("이체내역캡처", "06_이체내역캡처.png.txt")
                .addCompForm();
        long link = idContaining("자율배상신청서", "문자의 링크를 눌러 보안점검 앱을 내려받았습니다");
        long app = idContaining("사건사고사실확인원", "원격제어 기능이 있는 악성 앱을 설치하게");
        long rrn = idContaining("자율배상신청서", "주민등록번호 뒷자리를 물어 불러 주었습니다");
        long transfer = idContaining("자율배상신청서", "300만원을 직접 이체했고, OTP 번호도 불러 주었습니다");
        long account = idContaining("자율배상신청서", "안전계좌라며 KB국민은행 [받은계좌1]");
        llm = new FakeLlm("""
                {"tags": [
                  {"itemCode": "EX_SELF_ORDER", "text": "[피해자]가 [받은계좌1]로 300만원을 직접 이체했다",
                   "evidence": [{"fragmentId": %1$d, "quote": "KB국민은행 [받은계좌1]"}, {"fragmentId": %2$d, "quote": "300만원을 직접 이체했고"}]},
                  {"itemCode": "FT_GAVE_SECURITY", "text": "[피해자]가 통화 중 OTP 번호를 불러 주었다",
                   "evidence": [{"fragmentId": %2$d, "quote": "OTP 번호도 불러 주었습니다"}]},
                  {"itemCode": "FT_GAVE_PERSONAL", "text": "[피해자]가 본인 확인이라는 말에 주민등록번호 뒷자리를 불러 주었다",
                   "evidence": [{"fragmentId": %3$d, "quote": "주민등록번호 뒷자리를 물어 불러 주었습니다"}]},
                  {"itemCode": "FT_MALICIOUS_APP", "text": "[피해자]가 원격제어 기능이 있는 악성 앱을 설치했다",
                   "evidence": [{"fragmentId": %4$d, "quote": "원격제어 기능이 있는 악성 앱을 설치하게"}]},
                  {"itemCode": "FT_SMISHING_LINK", "text": "[피해자]가 문자의 링크를 눌러 보안점검 앱을 내려받았다",
                   "evidence": [{"fragmentId": %5$d, "quote": "문자의 링크를 눌러 보안점검 앱을 내려받았습니다"}]}]}"""
                .formatted(account, transfer, rrn, app, link));
        return new TaggingService(llm).tag(TestFiles.TAG_ITEMS, fragments, labels);
    }

    /** 서류에서 가린 글이 maskedText와 같은 nth번째 조각의 ID. 가짜 LLM 답에 실제 조각 ID를 넣을 때 쓴다. */
    long idOf(String document, String maskedText, int nth) {
        return fragments.stream().filter(f -> f.document().equals(document) && maskedText.equals(f.maskedText()))
                .skip(nth).findFirst().orElseThrow(() -> new AssertionError(document + "에 없음: " + maskedText)).id();
    }

    /** 서류에서 가린 글에 part가 든 첫 조각의 ID */
    long idContaining(String document, String part) {
        return fragments.stream().filter(f -> f.document().equals(document) && f.maskedText() != null
                && f.maskedText().contains(part)).findFirst().orElseThrow(() -> new AssertionError(part)).id();
    }

    private KimWooriCase addTexts(String document, List<String> texts) {
        MaskResult masked = new MaskService().maskForLlm(texts.stream().map(t -> new MaskInput(null, t)).toList(),
                KIM_WOORI_KNOWN, labels);
        labels = masked.labels();
        masked.texts().forEach(t -> fragments.add(new EvidenceFragment(fragments.size() + 1L, document, null, null, t)));
        return this;
    }
}
