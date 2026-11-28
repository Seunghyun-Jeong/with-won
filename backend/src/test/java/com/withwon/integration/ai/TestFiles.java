package com.withwon.integration.ai;

import static com.withwon.integration.ai.dto.CheckItem.Kind.EXCLUSION;
import static com.withwon.integration.ai.dto.CheckItem.Kind.FAULT;
import static com.withwon.integration.ai.dto.CheckItem.JudgeBy.DATA;
import static com.withwon.integration.ai.dto.CheckItem.JudgeBy.DOC;
import static com.withwon.integration.ai.dto.CheckItem.JudgeBy.DOC_RECORD;
import static com.withwon.integration.ai.dto.CheckItem.JudgeBy.MANUAL;
import static com.withwon.integration.ai.dto.CheckItem.Scope.INCIDENT;
import static com.withwon.integration.ai.dto.CheckItem.Scope.TX;
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
import java.util.Set;
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

    // 점검 항목 전체(쓰지 않는 EX_CORPORATE는 뺀 27개). 시트 "자율배상 점검 항목" 탭(주인)을 시트 순서대로 옮긴 사본이다.
    // check_item 시드가 생기면 시드에서 읽는다. 판정 시험과 TAG_ITEMS가 이 한 벌을 쓴다.
    public static final List<CheckItem> COMP_ITEMS = List.of(
            item("EX_PRIOR_RELIEF", EXCLUSION, INCIDENT, DATA, "이번 사고 전에 우리 은행에서 피해구제(환급 포함)나 배상 신청을 받은 적 있음"),
            item("FT_ID_LOST_UNREPORTED", FAULT, INCIDENT, DOC, "신분증을 잃어버리고 바로 분실신고를 하지 않음", "NO"),
            item("FT_ID_COPY_STORED", FAULT, INCIDENT, DOC, "신분증 사본을 휴대폰, PC, 클라우드에 저장", "YES"),
            item("FT_PW_MEMO", FAULT, INCIDENT, DOC, "계좌번호와 계좌 비밀번호를 저장(메모)", "YES"),
            item("FT_NO_LOCK", FAULT, INCIDENT, DOC, "휴대폰 잠금 없음", "NO"),
            item("EX_FAMILY", EXCLUSION, INCIDENT, DOC, "가족이나 지인이 한 거래", "YES"),
            item("EX_GOODS", EXCLUSION, INCIDENT, DOC, "물건 거래를 가장한 사기(물품대금, 중고거래, 쇼핑몰)", "YES"),
            item("EX_SERVICE", EXCLUSION, INCIDENT, DOC, "용역을 가장한 사기(중개 수수료, 게임아이템, 취업)", "YES"),
            item("EX_ILLEGAL", EXCLUSION, INCIDENT, DOC, "불법, 비정상 거래(몸캠, 로맨스 스캠, 조건만남)", "YES"),
            item("FT_SMISHING_LINK", FAULT, INCIDENT, DOC, "스미싱 문자나 카톡의 링크를 누름", "YES"),
            item("FT_MALICIOUS_APP", FAULT, INCIDENT, DOC, "출처 모를 앱(원격조정 앱 포함)을 설치, 실행", "YES"),
            item("EX_MEDIUM_TRANSFER", EXCLUSION, INCIDENT, DOC, "접근매체를 양도, 양수하거나 질권 설정(전자금융거래법 제6조 제3항)", "YES"),
            item("FT_GAVE_ID", FAULT, INCIDENT, DOC, "신분증을 줌(사본 사진이나 파일 전송 포함)", "YES"),
            item("FT_GAVE_PERSONAL", FAULT, INCIDENT, DOC, "개인정보(이름, 주민번호, 휴대폰번호)를 줌", "YES"),
            item("FT_GAVE_DEVICE", FAULT, INCIDENT, DOC, "휴대폰 등 기기를 줌(빌려준 경우 포함)", "YES"),
            item("FT_GAVE_ACCOUNT_PW", FAULT, INCIDENT, DOC, "계좌번호와 계좌 비밀번호를 줌", "YES"),
            item("FT_GAVE_SECURITY", FAULT, INCIDENT, DOC, "보안매체(OTP, 보안카드, 인증서)나 그 비밀번호를 줌", "YES"),
            item("FT_GAVE_OTHER", FAULT, INCIDENT, DOC, "그 밖의 전자금융 정보를 줌", "YES"),
            item("EX_SELF_ORDER", EXCLUSION, TX, DOC_RECORD,
                    "피해자가 직접 이체를 지시함(가족 사칭, 협박, 대출사기처럼 제3자가 시킨 경우 포함)", "YES"),
            item("EX_WARNED", EXCLUSION, TX, DATA, "은행의 의심거래 안내에도 정상 거래라고 주장"),
            item("EX_FACE", EXCLUSION, TX, DATA, "창구 등 대면 거래(앱으로 한 ATM 출금은 대상)"),
            item("EX_CARD", EXCLUSION, TX, DATA, "신용카드, 체크카드 결제"),
            item("EX_EFIN", EXCLUSION, TX, DATA, "전자금융업자(OO페이 등)를 거친 거래"),
            item("EX_LAWSUIT", EXCLUSION, INCIDENT, MANUAL, "은행과 소송 중이거나 판결이 확정됨", "YES"),
            item("EX_SETTLED", EXCLUSION, INCIDENT, MANUAL, "은행과 화해(합의)가 성립함", "YES"),
            item("EX_POLICE_NOT_FRAUD", EXCLUSION, INCIDENT, MANUAL, "수사 결과 전자금융사고가 아님"),
            item("EX_NOT_FRAUD", EXCLUSION, INCIDENT, MANUAL, "사실관계상 전자금융사고로 보기 어려움"));

    // 태깅 대상은 COMP_ITEMS에서 판정이 서류나 기록+서류인 항목이다. 태깅과 진짜 Claude 시험이 같이 쓴다.
    public static final List<CheckItem> TAG_ITEMS = COMP_ITEMS.stream()
            .filter(i -> i.judgeBy() == DOC || i.judgeBy() == DOC_RECORD).toList();

    private TestFiles() {
    }

    private static CheckItem item(String code, CheckItem.Kind kind, CheckItem.Scope scope, CheckItem.JudgeBy judgeBy,
            String name, String... hitAnswers) {
        return new CheckItem(code, kind, name, scope, judgeBy, Set.of(hitAnswers));
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
