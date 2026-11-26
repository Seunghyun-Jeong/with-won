package com.withwon.integration.ai;

import static com.withwon.integration.ai.dto.CheckItem.Kind.EXCLUSION;
import static com.withwon.integration.ai.dto.CheckItem.Kind.FAULT;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.withwon.integration.ai.dto.CaseKnown;
import com.withwon.integration.ai.dto.CheckItem;
import com.withwon.integration.ai.dto.CaseKnown.Payee;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 테스트가 쓰는 파일: 즉석에서 만드는 PDF와 이미지, git에 없는 김우리 시험 서류. */
public final class TestFiles {

    // 저장소에는 올리지 않는 시험 서류(.git/info/exclude). 없으면 이 서류를 쓰는 테스트는 건너뛴다.
    public static final Path KIM_WOORI = Path.of("src/test/resources/ai/samples/김우리");

    // 김우리 사건에서 이미 아는 값(신분증, 신청서, 원장). 가림과 타임라인 시험이 같이 쓴다.
    public static final CaseKnown KIM_WOORI_KNOWN = new CaseKnown(
            List.of("900512-2123456"),
            "김우리",
            "010-2847-5931",
            "kimwoori@example.com",
            "서울특별시 성동구 왕십리로 123, 101동 1004호 (행당동, 한빛아파트)",
            List.of("1002-384-729105"),
            List.of(new Payee("482901-01-336417", "박민수"), new Payee("110-472-836150", "이정호")),
            List.of("우리은행 왕십리지점"));

    // 태깅 대상 점검 항목. 시트 "자율배상 점검 항목" 탭(주인)에서 판정이 서류나 기록+서류인 18개를 순서대로 옮겼다.
    // check_item 시드가 생기면 시드에서 읽는다. 태깅과 진짜 Claude 시험이 같이 쓴다.
    public static final List<CheckItem> TAG_ITEMS = List.of(
            new CheckItem("FT_ID_LOST_UNREPORTED", FAULT, "신분증을 잃어버리고 바로 분실신고를 하지 않음"),
            new CheckItem("FT_ID_COPY_STORED", FAULT, "신분증 사본을 휴대폰, PC, 클라우드에 저장"),
            new CheckItem("FT_PW_MEMO", FAULT, "계좌번호와 계좌 비밀번호를 저장(메모)"),
            new CheckItem("FT_NO_LOCK", FAULT, "휴대폰 잠금 없음"),
            new CheckItem("EX_FAMILY", EXCLUSION, "가족이나 지인이 한 거래"),
            new CheckItem("EX_GOODS", EXCLUSION, "물건 거래를 가장한 사기(물품대금, 중고거래, 쇼핑몰)"),
            new CheckItem("EX_SERVICE", EXCLUSION, "용역을 가장한 사기(중개 수수료, 게임아이템, 취업)"),
            new CheckItem("EX_ILLEGAL", EXCLUSION, "불법, 비정상 거래(몸캠, 로맨스 스캠, 조건만남)"),
            new CheckItem("FT_SMISHING_LINK", FAULT, "스미싱 문자나 카톡의 링크를 누름"),
            new CheckItem("FT_MALICIOUS_APP", FAULT, "출처 모를 앱(원격조정 앱 포함)을 설치, 실행"),
            new CheckItem("EX_MEDIUM_TRANSFER", EXCLUSION, "접근매체를 양도, 양수하거나 질권 설정(전자금융거래법 제6조 제3항)"),
            new CheckItem("FT_GAVE_ID", FAULT, "신분증을 줌(사본 사진이나 파일 전송 포함)"),
            new CheckItem("FT_GAVE_PERSONAL", FAULT, "개인정보(이름, 주민번호, 휴대폰번호)를 줌"),
            new CheckItem("FT_GAVE_DEVICE", FAULT, "휴대폰 등 기기를 줌(빌려준 경우 포함)"),
            new CheckItem("FT_GAVE_ACCOUNT_PW", FAULT, "계좌번호와 계좌 비밀번호를 줌"),
            new CheckItem("FT_GAVE_SECURITY", FAULT, "보안매체(OTP, 보안카드, 인증서)나 그 비밀번호를 줌"),
            new CheckItem("FT_GAVE_OTHER", FAULT, "그 밖의 전자금융 정보를 줌"),
            new CheckItem("EX_SELF_ORDER", EXCLUSION,
                    "피해자가 직접 이체를 지시함(가족 사칭, 협박, 대출사기처럼 제3자가 시킨 경우 포함)"));

    private TestFiles() {
    }

    /** 쪽마다 글 한 줄을 쓴 PDF. 빈 문자열인 쪽은 글자 층이 없는 쪽(스캔본처럼)이 된다. */
    public static byte[] pdf(String... pageTexts) throws IOException {
        return pdfPages(Arrays.stream(pageTexts).map(t -> t.isEmpty() ? List.<String>of() : List.of(t)).toList());
    }

    /** 한 쪽의 같은 높이에 칸들을 200pt씩 띄워 쓴 PDF. 표의 한 행처럼. */
    public static byte[] pdfRow(String... cells) throws IOException {
        return pdfPages(List.of(List.of(cells)));
    }

    // 쪽마다 칸들을 왼쪽 72pt부터 200pt 간격으로, 아래에서 700pt 높이에 12pt로 쓴다.
    private static byte[] pdfPages(List<List<String>> pages) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (List<String> cells : pages) {
                var page = new PDPage();
                doc.addPage(page);
                if (cells.isEmpty()) {
                    continue;
                }
                try (var content = new PDPageContentStream(doc, page)) {
                    for (int i = 0; i < cells.size(); i++) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72 + 200 * i, 700);
                        content.showText(cells.get(i));
                        content.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] blankPdf(int pages) throws IOException {
        var texts = new String[pages];
        Arrays.fill(texts, "");
        return pdf(texts);
    }

    public static byte[] image(String format, int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    public static byte[] kimWoori(String fileName) throws IOException {
        Path file = KIM_WOORI.resolve(fileName);
        assumeTrue(Files.exists(file), "김우리 시험 서류가 없어 건너뜀: " + file);
        return Files.readAllBytes(file);
    }

    public static JsonNode kimWooriAnswer() throws IOException {
        return new JsonMapper().readTree(kimWoori("정답.json"));
    }

    /** NAVER Cloud 공식 문서의 General OCR V2 응답 예시. */
    public static String officialClovaExample() throws IOException {
        return resource("/ai/clova/general-v2-response.json");
    }

    /** NAVER Cloud 공식 문서의 CLOVA Speech 로컬 파일 인식 sync 응답 예시. */
    public static String officialSpeechExample() throws IOException {
        return resource("/ai/clova/speech-sync-response.json");
    }

    private static String resource(String path) throws IOException {
        try (var in = TestFiles.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 앱(application.properties의 spring.config.import)과 같은 backend/.env를 같은 properties 형식으로 읽는다. 없으면 빈 값. */
    public static Properties env() throws IOException {
        Path env = Path.of(".env");
        return Files.exists(env) ? PropertiesLoaderUtils.loadProperties(new FileSystemResource(env)) : new Properties();
    }
}
