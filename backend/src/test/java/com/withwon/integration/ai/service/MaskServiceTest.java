package com.withwon.integration.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.MaskInput;
import com.withwon.integration.ai.dto.MaskKnown;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// 글은 김우리 서류(CLOVA OCR)와 통화녹음2(CLOVA Speech)에서 실제로 읽힌 모양 그대로다(build/ocr-kim-woori, build/speech-kim-woori, 11-25).
class MaskServiceTest {

    private static final MaskKnown KIM_WOORI = new MaskKnown(
            List.of("900512-2123456"),
            List.of("1002-384-729105", "482901-01-336417", "110-472-836150"));

    private final MaskService service = new MaskService();

    private List<String> lines(String... texts) {
        return service.mask(Arrays.stream(texts).map(t -> new MaskInput(null, t)).toList(), KIM_WOORI);
    }

    private static MaskInput a(String text) {
        return new MaskInput("A", text);
    }

    private static MaskInput b(String text) {
        return new MaskInput("B", text);
    }

    @Test
    void 신분증_주민번호는_가린다() {
        assertThat(lines("900512-2123456").get(0)).doesNotContainPattern("\\d");
    }

    @Test
    void 서류의_이름_전화번호_아는_계좌_날짜_금액_주소는_그대로_둔다() {
        String[] kept = {
                "김우리",
                "010-2847-5931",
                "070-4512-3387",
                "1002-384-729105",
                "민은행 482901-01-336417(예금주 박민수)로 3,000,000원, 신한은행 110-472-836150(예금주 이정호)",
                "원격제어 기능이 있는 악성 앱을 설치하게 한 뒤 피해자 명의 우리은행 계좌(1002-384-729105)에서 KB국",
                "금 12,500,000원 (3,000,000원, 9,500,000원 2회 이체) 피해",
                "2026. 11. 18. 13:50 ~ 15:10경",
                "서울특별시 성동구 왕십리로 123, 101동 1004호 (행당동, 한빛아파트)",
                "1990. 5. 12.",
        };
        assertThat(lines(kept)).containsExactly(kept);
    }

    @Test
    void 아는_계좌가_아닌_긴_숫자는_기본으로_가린다() {
        List<String> masked = lines(
                "적힌 번호 4111-1111-1111-1111",
                "우선 300만 원을 카카오톡으로 보내드린 국민은행 사파리 901 01 33647 예금주 안민수 계좌로 보내주세요.");

        assertThat(masked.get(0)).doesNotContain("4111");
        assertThat(masked.get(1)).doesNotContain("901").doesNotContain("33647")
                .contains("300만 원", "국민은행", "예금주 안민수");
    }

    @Test
    void 질문_단어_뒤_같은_문장의_숫자는_가린다() {
        List<String> masked = lines("OTP 번호는 738019입니다. 오후 3시 10분에 이체됐습니다.");

        assertThat(masked.get(0)).doesNotContain("738019").contains("오후 3시 10분");
    }

    @Test
    void 통화_전화음질_주민번호_뒷자리와_OTP는_질문_다음_답에서_가린다() {
        List<MaskInput> call = List.of(
                a("본인 확인을 위해 주민등록번호 뒷자리를 불러주세요."),
                b("212 3, 4, 5 6이요."),
                a("확인됐습니다."),
                b("네 지금 보냈어요. 300만 원이요."),
                a("좋습니다. 나머지는 저희가 앱으로 점검하겠습니다. otp 번호 6자리 불러주시겠어요?"),
                b("7 3 8 0 1 구요."),
                a("네 화면 끄지 마시고 그대로 기다리세요. 다시 연락드리겠습니다."));

        List<String> masked = service.mask(call, KIM_WOORI);

        assertThat(masked.get(1)).doesNotContainPattern("\\d");
        assertThat(masked.get(3)).isEqualTo("네 지금 보냈어요. 300만 원이요.");
        assertThat(masked.get(5)).doesNotContainPattern("\\d").doesNotContain("구");
        assertThat(masked.get(6)).isEqualTo(call.get(6).text());
    }

    @Test
    void 통화_깨끗한음질의_띄어_쓴_숫자도_가린다() {
        List<String> masked = service.mask(List.of(
                a("본인 확인을 위해 주민등록번호 뒷자리를 불러주세요."),
                b("2 1 2 3, 4, 5 6이요."),
                a("otp 번호 6자리 불러주시겠어요"),
                b("7 3 8 0 1 9요.")), KIM_WOORI);

        assertThat(masked.get(1)).doesNotContainPattern("\\d");
        assertThat(masked.get(3)).doesNotContainPattern("\\d");
    }

    @Test
    void 대본처럼_한글_숫자로_불러도_가린다() {
        List<String> masked = service.mask(List.of(
                a("본인 확인을 위해 주민등록번호 뒷자리를 불러 주세요."),
                b("이, 일, 이, 삼, 사, 오, 육이요."),
                a("오티피 번호 여섯 자리 불러 주시겠어요?"),
                b("칠, 삼, 팔, 공, 일, 구요.")), KIM_WOORI);

        assertThat(masked.get(1)).doesNotContain("이, 일, 이, 삼");
        assertThat(masked.get(3)).doesNotContain("칠, 삼, 팔");
    }

    @Test
    void 나눠_부른_번호는_가리고_가려진_것_없이_두_조각이_지나면_그_뒤는_남긴다() {
        List<String> masked = service.mask(List.of(
                a("비밀번호 네 자리 눌러 주세요."),
                b("잠깐만요."),
                b("1 2 3 4요."),
                a("네 확인했습니다."),
                b("네."),
                a("그럼 다음으로 넘어가겠습니다."),
                b("2 3번 계좌요?")), KIM_WOORI);

        assertThat(masked.get(2)).doesNotContainPattern("\\d");
        assertThat(masked.get(6)).isEqualTo("2 3번 계좌요?");
    }

    @Test
    void 가린_글에는_무엇을_가렸는지_표시가_남는다() {
        List<String> masked = service.mask(List.of(
                a("otp 번호 6자리 불러주시겠어요?"),
                b("7 3 8 0 1 9요.")), KIM_WOORI);

        assertThat(masked.get(1)).isEqualTo("[OTP]요.");
    }

    @Test
    void 아는_주민번호가_가림을_빠져나가면_그_조각만_실패로_null() {
        // 낱말로 끊겨 짧은 숫자 둘로 보이지만, 이어 붙이면 신분증 주민번호 뒷자리다.
        List<String> masked = lines("뒷자리는 212번 다음 3456", "김우리");

        assertThat(masked.get(0)).isNull();
        assertThat(masked.get(1)).isEqualTo("김우리");
    }

    @Test
    void 아는_주민번호가_이웃한_두_조각에_나뉘어_남으면_둘_다_실패로_null() {
        assertThat(lines("뒷자리 212", "3456", "김우리")).containsExactly(null, null, "김우리");
    }

    @Test
    void 양식_칸을_한_글자씩_읽어_줄이_나뉜_번호는_모두_실패로_null() {
        List<String> known = lines("9 0 0 5 1 2 2 1 2 3 4 5 6".split(" "));
        List<String> unknown = lines("8 5 0 3 0 1 1 2 3 4 5 6 7".split(" "));

        assertThat(known).containsOnlyNulls();
        assertThat(unknown).containsOnlyNulls();
    }

    // 아래는 김우리 결과에는 없지만 실제 통화와 서류에서 나올 법한 모양이다.

    @Test
    void 화자_구분이_틀려_답이_질문자_발화로_묶여도_가린다() {
        List<String> masked = service.mask(List.of(
                a("otp 번호 6자리 불러주시겠어요?"),
                a("7 3 8 0 1 9요."),
                a("네 확인됐습니다. 12 34")), KIM_WOORI);

        assertThat(masked.get(1)).doesNotContainPattern("\\d");
        assertThat(masked.get(2)).doesNotContainPattern("\\d").doesNotContain("[OTP]");
    }

    @Test
    void 질문과_답이_한_발화로_합쳐져도_답은_가리고_자릿수는_남긴다() {
        List<String> masked = service.mask(
                List.of(a("otp 번호 6자리 불러주시겠어요? 738 019요.")), KIM_WOORI);

        assertThat(masked.get(0)).isEqualTo("otp 번호 6자리 불러주시겠어요? [OTP]요.");
    }

    @Test
    void 질문_단어_없이_번호를_불러_달라고_해도_답을_가린다() {
        List<String> masked = service.mask(List.of(
                a("앱에 뜬 번호 불러 주세요."),
                b("73 80요.")), KIM_WOORI);

        assertThat(masked.get(1)).doesNotContainPattern("\\d");
    }

    @Test
    void 질문이_아예_안_읽혀도_한_자리씩_부른_숫자와_대화_속_홀로_있는_숫자는_가린다() {
        List<String> masked = service.mask(List.of(
                b("7 3 8 0요."),
                b("738019요."),
                b("칠 삼 팔 공이요.")), KIM_WOORI);

        assertThat(masked).allSatisfy(m -> assertThat(m).doesNotContainPattern("\\d").doesNotContain("칠 삼"));
    }

    @Test
    void 붙여_읽은_한글_숫자와_고유어_숫자도_가린다() {
        List<String> masked = service.mask(List.of(
                a("주민등록번호 뒷자리를 불러 주세요."),
                b("이일이삼사오육이요."),
                a("비밀번호 네 자리 눌러 주세요."),
                b("하나 둘 삼 넷이요."),
                a("네."),
                b("칠삼팔공일구요.")), KIM_WOORI);

        assertThat(masked.get(1)).isEqualTo("[주민번호]요.");
        assertThat(masked.get(3)).isEqualTo("[비밀번호]이요.");
        assertThat(masked.get(5)).doesNotContain("칠삼팔");
    }

    @Test
    void 마침표로_끊기거나_다시로_이은_숫자도_한_덩어리로_가린다() {
        List<String> masked = lines("2. 1. 2. 3. 4. 5. 6.", "850301 다시 1234567", "９００５１２–２１２３４５６",
                "4111.1111.1111.1111", "850301.1234.567");

        assertThat(masked).allSatisfy(m -> assertThat(m).doesNotContainPattern("[0-9０-９]"));
    }

    @Test
    void 여권번호_운전면허번호_외국인등록번호_모양을_가린다() {
        List<String> masked = lines("M123A4567", "여권 M12345678", "11-12-345678-90", "850301-5234567");

        assertThat(masked).containsExactly("[여권번호]", "여권 [여권번호]", "[가린 번호]", "[주민번호]");
    }

    @Test
    void 문자_캡처의_인증번호는_같은_줄과_다음_줄에서_가린다() {
        List<String> masked = lines("[Web발신] 인증번호 [482913] 타인에게 알리지 마세요", "인증번호", "482913", "15:42");

        assertThat(masked.get(0)).doesNotContain("482913");
        assertThat(masked.get(2)).isEqualTo("[인증번호]");
        assertThat(masked.get(3)).isEqualTo("15:42");
    }

    @Test
    void 단위가_붙은_금액_시각_주소는_질문_맥락에서도_남긴다() {
        List<MaskInput> call = List.of(
                a("비밀번호 불러 주세요."),
                b("잠깐만요. 오후 3시 10분에 300만 원 보냈는데요."),
                b("3000000원이요. 12,500,000원 아니고요."));

        assertThat(service.mask(call, KIM_WOORI))
                .containsExactlyElementsOf(call.stream().map(MaskInput::text).toList());
    }

    @Test
    void 전화번호는_일반_질문_뒤에는_남기고_비밀번호_질문_뒤에는_가린다() {
        List<String> masked = service.mask(List.of(
                a("전화번호 불러 주세요."),
                b("010 2847 5931이요."),
                a("비밀번호 불러 주세요."),
                b("01028475931이요.")), KIM_WOORI);

        assertThat(masked.get(1)).isEqualTo("010 2847 5931이요.");
        assertThat(masked.get(3)).isEqualTo("[비밀번호]이요.");
    }

    @Test
    void 대표번호는_하이픈으로_적힌_것만_남긴다() {
        assertThat(lines("1588-1234", "1 5 8 8 1 2 3 4")).containsExactly("1588-1234", "[가린 번호]");
    }

    @Test
    void 숫자와_한글_숫자를_붙여_섞어_써도_한_덩어리로_가린다() {
        List<String> masked = lines(
                "공10삼4오륙789공",
                "공 10 삼 4 오륙 789 공",
                "9005일2-2일2삼4오6",
                "칠3팔0일9",
                "2026. 11. 18일 오후 3시, 5일 뒤 2일8시");

        assertThat(masked.subList(0, 4)).containsOnly("[가린 번호]", "[주민번호]");
        assertThat(masked.get(4)).isEqualTo("2026. 11. 18일 오후 3시, 5일 뒤 2일8시");
    }

    // 아래는 11-26에 다른 모델(ChatGPT)에게 규칙을 주고 받은 반례와, 직접 돌려 찾은 구멍이다.

    private List<String> talk(String... turns) {
        return service.mask(Arrays.stream(turns).map(t -> new MaskInput(t.substring(0, 1), t.substring(2))).toList(),
                KIM_WOORI);
    }

    private static void assertGone(String masked, String... secrets) {
        assertThat(masked).isNotNull().doesNotContainPattern("[0-9]");
        for (String secret : secrets) {
            assertThat(masked).doesNotContain(secret);
        }
    }

    @Test
    void 비밀_질문_뒤에서는_두_글자씩_읽은_한글_숫자도_가린다() {
        assertGone(talk("A OTP는 칠삼 팔공 일구예요.").get(0), "칠삼", "팔공", "일구");
        assertGone(talk("A 비밀번호는 이사 이사예요.").get(0), "이사");
        assertGone(talk("A OTP는 칠삼이랑 팔공이랑 일구예요.").get(0), "칠삼", "팔공", "일구");
        assertGone(talk("A 비밀번호는 공공이 아니라 공일이에요.").get(0), "공공", "공일");
        assertThat(talk("A 인증번호 말씀해 주세요.", "B 칠삼", "B 팔공", "B 일구").subList(1, 4))
                .containsOnly("[인증번호]");
    }

    @Test
    void 숫자_뒤에_어미가_붙어도_가린다() {
        assertGone(talk("A 비밀번호는 칠삼팔공일구거든요.").get(0), "칠삼팔");
        assertGone(talk("A 인증번호 칠삼팔공일구인데요.").get(0), "칠삼팔");
        assertGone(talk("A 비밀번호는 일곱둘 여덟셋이에요.").get(0), "일곱", "여덟");
        assertGone(talk("B 구공공오일이에 이일이삼사오육이요").get(0), "구공공", "이일이");
    }

    @Test
    void 수로_읽거나_영어로_읽은_번호도_비밀_질문_뒤에서는_가린다() {
        assertGone(talk("B 비밀번호는 천이백삼십사요").get(0), "천이백");
        assertGone(talk("B OTP는 칠십삼 팔십 십구요").get(0), "칠십삼", "팔십", "십구");
        assertGone(talk("A OTP는 seven three eight zero one nine.").get(0), "seven", "nine");
        assertGone(talk("A 인증번호는 세븐 쓰리 에잇 제로 원 나인.").get(0), "세븐", "나인");
        assertGone(talk("A 비밀번호는 더블 쓰리 더블 세븐.").get(0), "쓰리", "세븐");
        assertGone(talk("A 비밀번호는 쌍삼 쌍칠.").get(0), "쌍삼", "쌍칠");
        assertGone(talk("B 칠 삼 팔 빵 일 구요").get(0), "칠 삼", "빵");
    }

    @Test
    void 글자가_섞인_비밀번호와_코드는_낱말째_가린다() {
        assertGone(talk("B 비밀번호는 woori1234!예요").get(0), "woori");
        assertGone(talk("B 인증코드 A1B2C3 입니다").get(0), "A", "B", "C");
    }

    @Test
    void 질문_없이도_구분자나_낱말로_끊어_부른_번호를_가린다() {
        for (String said : List.of("73, 80, 19입니다.", "73. 80. 19입니다.", "73:80:19입니다.", "73·80·19입니다.",
                "73 / 80 / 19입니다.", "73 하고 80 그다음 19요.", "73 맞고요, 80 맞고요, 19예요.",
                "첫 칸 73, 가운데 칸 80, 끝 칸 19.", "11 그리고 26 그리고 123 그리고 456 그리고 78.")) {
            assertGone(talk("A " + said).get(0));
        }
    }

    @Test
    void OCR이_칸_구분자를_끼우거나_글자를_잘못_읽어도_가린다() {
        List<String> masked = lines("90|05|12|2|12|34|56", "90/05/12/2/12/34/56", "90.05.12.2.12.34.56",
                "90|05|12|5|12|34|56", "11|26|123|456|78", "4111|1111|1111|1111", "4111/1111/1111/1111",
                "41|11|11|11|11|11|11|11", "9OO512-2I23456", "①②③④⑤⑥⑦");

        assertThat(masked).allSatisfy(m -> assertThat(m).isNotNull().doesNotContainPattern("[0-9OI①-⑨]"));
        assertThat(lines("90년 05월 12일 / 2·12·34·56").get(0)).doesNotContain("12·34");
    }

    @Test
    void 칸_이름이_띄어_읽히거나_영어여도_값을_가린다() {
        List<String> masked = lines("카 드 번 호 : 41|11|11|11|11|11|11|11", "인 증 번 호 : 73|80|19", "EXP 11/28",
                "VALID THRU 11/28", "민증번호 850301-1234567");

        assertThat(masked).allSatisfy(m -> assertGone(m));
        assertGone(talk("B 씨브이씨는 123이요").get(0));
    }

    @Test
    void 값이_먼저_나오고_이름이_뒤에_와도_가린다() {
        assertGone(talk("A 742가 CVC예요.").get(0));
        assertGone(talk("A 11/28, 카드 유효기간입니다.").get(0));
        assertGone(lines("742 (CVC)").get(0));
        assertGone(lines("11/28 (유효기간)").get(0));
        assertThat(lines("11/28", "유효기간").get(0)).isEqualTo("[카드번호]");
    }

    @Test
    void 질문자가_다시_말한_뒤에_나온_답도_가린다() {
        List<String> otp = talk("A OTP 번호 불러주세요", "B 네 잠시만요", "A 천천히 하세요", "B 738", "B 019요");
        List<String> card = talk("A 보안카드 17번 불러주세요", "B 어디 있더라", "A 지갑에 있을 겁니다", "B 아 여기 있네요",
                "B 23이요");

        assertThat(otp.subList(1, 5)).containsExactly("네 잠시만요", "천천히 하세요", "[OTP]", "[OTP]요");
        assertThat(card.get(4)).isEqualTo("[보안카드번호]이요");
    }

    @Test
    void 목록에_없던_말로_번호를_물어도_답을_가린다() {
        assertGone(talk("A 번호가 어떻게 되세요?", "B 73, 8요.").get(1));
        assertGone(talk("A 문자에 뜬 거 그대로 보내 줘.", "B 73·8").get(1));
        assertGone(talk("A 오피티 번호 불러주세요", "B 73 8요").get(1));
    }

    @Test
    void 단위나_쉼표가_붙어도_비밀_질문_뒤에서는_가린다() {
        assertGone(talk("A 비밀번호는 4826만요.").get(0));
        assertGone(talk("A 인증번호는 738019일까요?").get(0));
        assertGone(talk("A 비밀번호는 73,819예요.").get(0));
        assertGone(talk("A OTP는 738,019예요.").get(0));
        assertGone(talk("B 비밀번호 1004호 아니고 2580번이요").get(0));
    }

    @Test
    void 날짜와_시각과_금액은_모양이_달라도_남긴다() {
        String[] kept = {
                "B 2026-11-18 14:32",
                "B 20261118에 보냈어요.",
                "B 2026 11 18",
                "B 14시 32분에 3000000 원 보냈어요.",
                "B 송금액은 ₩3000000입니다.",
                "B 주민센터에서 11/18에 확인했어요.",
                "B 비밀번호는 알려주지 않았고 14:32에 통화했어요.",
        };
        assertThat(talk(kept)).containsExactlyElementsOf(Arrays.stream(kept).map(k -> k.substring(2)).toList());
        assertThat(talk("A 인증번호 말씀해 주세요.", "B 3000 원 보냈어요.").get(1)).isEqualTo("3000 원 보냈어요.");
    }

    @Test
    void 마침표나_국가번호로_적은_전화번호는_통째로_남긴다() {
        assertThat(lines("010.3456.7890", "+82 10-3456-7890")).containsExactly("010.3456.7890", "+82 10-3456-7890");
    }

    @Test
    void 비밀_질문_뒤의_평범한_말은_숫자_글자가_들어_있어도_남긴다() {
        // 가짜 "[OTP]" 표시가 생기면 LLM이 피해자가 번호를 불러 줬다고 오해한다.
        for (String said : List.of("네 일이 좀 있어서 잠시만요", "이사 온 지 3년 됐어요", "천천히 할게요 일단 사이트 들어갔어요",
                "삼십 분 전에 문자 왔어요", "저는 이천에 살아요", "일일이 다 확인했어요", "그 사이 일이 많았어요",
                "오후 2시 30분쯤 300만 원 보냈고 계좌는 1002-384-729105예요")) {
            assertThat(talk("A OTP 번호 불러주세요", "B " + said).get(1)).isEqualTo(said);
        }
    }

    // 아래는 11-26에 바뀐 규칙을 다시 주고 받은 2차 반례다.

    @Test
    void 비밀_이름에_바로_붙은_값은_날짜나_시각_모양이어도_가린다() {
        assertGone(talk("A 인증번호는 12:34로 표시돼요.").get(0));
        assertGone(talk("A 비밀번호는 2026-11-26입니다.").get(0));
        assertGone(talk("A 확인번호는 11월 26일로 나와요.").get(0));
        assertGone(talk("A 비밀번호 불러 주세요.", "B 5월 12일이요.").get(1));
        for (String kept : List.of("인증번호 확인은 2026-11-26 14:32에 했고 3,000,000원을 보냈습니다.",
                "인증번호는 3분 안에 입력하세요.", "인증번호 확인은 14:32:05에 끝났습니다.")) {
            assertThat(talk("A " + kept).get(0)).isEqualTo(kept);
        }
    }

    @Test
    void 질문이_안_읽힌_답이_날짜나_동호수_모양뿐이면_가린다() {
        for (String said : List.of("20261126입니다.", "11/26이에요.", "4826호입니다.", "482,619라고 나와요.")) {
            assertGone(talk("B " + said).get(0));
        }
        for (String kept : List.of("11/18에 보냈어요.", "1004호에 살아요.", "납부 대상 월은 2026-11입니다.")) {
            assertThat(talk("B " + kept).get(0)).isEqualTo(kept);
        }
    }

    @Test
    void 답을_나눠_말하는_사이에_질문자가_끼어들거나_되물어도_가린다() {
        assertGone(talk("A 인증번호 알려 주세요.", "B 앞은 482예요.", "A 네, 계속하세요.", "B 뒤는 619예요.").get(3));
        List<String> echo = talk("A 인증번호 알려 주세요.", "B 인증번호 말씀이세요?", "A 네.", "B 잠시만요.", "B 앞은 482예요.",
                "B 뒤는 619예요.");
        assertThat(echo.subList(4, 6)).containsExactly("앞은 [인증번호]예요.", "뒤는 [인증번호]예요.");
        assertThat(talk("A 비밀번호 불러 주세요.", "B 잠깐만요.", "A 네.", "B 앱을 여는 중이에요.", "A 기다릴게요.",
                "B 화면이 아직 안 떠요.", "A 천천히 하세요.", "B ab73kq").get(7)).isEqualTo("[비밀번호]");
        assertThat(talk("A 비밀번호 불러 주세요.", "B 접수번호는 874291입니다.", "A 네, 기다릴게요.", "B ab73kq").get(3))
                .isEqualTo("[비밀번호]");
    }

    @Test
    void 이름_없이_화면의_숫자나_코드를_달라는_말_뒤에는_전화번호_모양도_가린다() {
        List<String> digits = talk("A 숫자를 화면에 보이는 순서 그대로 천천히 불러 주세요.", "B 482부터요.", "B 619예요.");
        assertGone(digits.get(1));
        assertGone(digits.get(2));
        assertGone(talk("A 로그인할 때 쓰는 코드를 알려 주세요.", "B 010-4826-1937").get(1));
        assertGone(service.mask(List.of(a("비밀번호는 I23456789O12입니다.")),
                new MaskKnown(List.of(), List.of("123456789012"))).get(0));
    }

    @Test
    void 재검사는_정상_거래_줄과_표의_순번을_떨어뜨리지_않는다() {
        String[] rows = {"2026-11-26 14:32 4,829", "2012-01-31 / 12,345"};
        String[] order = {"1", "2", "3", "4", "5", "6"};
        String account = "1234567812345670";

        assertThat(lines(rows)).containsExactly(rows);
        assertThat(lines(order)).containsExactly(order);
        assertThat(service.mask(List.of(new MaskInput(null, account)), new MaskKnown(List.of(), List.of(account))))
                .containsExactly(account);
    }

    @Test
    void 여러_줄에_나뉜_아는_주민번호와_카드번호는_실패로_null() {
        List<MaskInput> rrn = Arrays.stream(new String[] {"1", "23", "45", "67"}).map(t -> new MaskInput(null, t)).toList();

        assertThat(service.mask(rrn, new MaskKnown(List.of("900101-1234567"), List.of()))).containsOnlyNulls();
        assertThat(lines("378", "282", "246", "310", "005")).containsOnlyNulls();
    }
}
