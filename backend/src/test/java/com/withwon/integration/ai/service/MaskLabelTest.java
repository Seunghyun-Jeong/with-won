package com.withwon.integration.ai.service;

import static com.withwon.integration.ai.TestFiles.KIM_WOORI_KNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.withwon.integration.ai.dto.MaskInput;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// 이름표 전환. 글은 김우리 서류(CLOVA OCR)와 통화녹음(CLOVA Speech)에서 실제로 읽힌 모양 그대로다(build/ocr-kim-woori, build/speech-kim-woori, 11-25).
class MaskLabelTest {

    private final MaskService service = new MaskService();

    private MaskResult lines(List<MaskLabel> existing, String... texts) {
        return service.maskForLlm(Arrays.stream(texts).map(t -> new MaskInput(null, t)).toList(), KIM_WOORI_KNOWN, existing);
    }

    private List<String> lines(String... texts) {
        return lines(List.of(), texts).texts();
    }

    private List<String> talk(String... texts) {
        return service.maskForLlm(Arrays.stream(texts).map(t -> new MaskInput("A", t)).toList(), KIM_WOORI_KNOWN, List.of())
                .texts();
    }

    @Test
    void 아는_사람과_번호와_주소는_역할_이름표로_바꾼다() {
        assertThat(lines(
                "김우리",
                "010-2847-5931",
                "kimwoori@example.com",
                "서울특별시 성동구 왕십리로 123, 101동 1004호 (행당동, 한빛아파트)",
                "원격제어 기능이 있는 악성 앱을 설치하게 한 뒤 피해자 명의 우리은행 계좌(1002-384-729105)에서 KB국",
                "민은행 482901-01-336417(예금주 박민수)로 3,000,000원, 신한은행 110-472-836150(예금주 이정호)"))
                .containsExactly(
                        "[피해자]",
                        "[피해자전화]",
                        "[피해자메일]",
                        "[피해자주소]",
                        "원격제어 기능이 있는 악성 앱을 설치하게 한 뒤 피해자 명의 우리은행 계좌([피해자계좌])에서 KB국",
                        "민은행 [받은계좌1](예금주 [예금주1])로 3,000,000원, 신한은행 [받은계좌2](예금주 [예금주2])");
    }

    @Test
    void 날짜_시각_금액_사칭_기관은_남긴다() {
        String[] kept = {
                "2026. 11. 18. 13:50 ~ 15:10경",
                "금 12,500,000원 (3,000,000원, 9,500,000원 2회 이체) 피해",
                "[서울중앙지검] 사건 관련 개인정보 보호를 위해",
        };
        assertThat(lines(kept)).containsExactly(kept);
    }

    @Test
    void 성을_뺀_이름은_호칭이_붙을_때만_바꾸고_은행_이름은_남긴다() {
        assertThat(lines("[서울중앙지검] 김우리님 사건(2026형", "우리 씨 계좌가 우리은행이죠"))
                .containsExactly("[서울중앙지검] [피해자]님 사건(2026형", "[피해자] 씨 계좌가 우리은행이죠");
    }

    @Test
    void 주소_낱말과_지점_이름에서_뽑은_지역은_같은_이름표로_바꾸고_시도는_남긴다() {
        assertThat(lines("왕십리지점", "서울 성동구 행당동 한빛아파트 앞", "왕십리로 123"))
                .containsExactly("[지역2]지점", "서울 [지역1]구 [지역3]동 [지역4]아파트 앞", "[지역2]로 123");
    }

    @Test
    void 모르는_전화번호와_메일은_나온_순서대로_번호를_매긴다() {
        assertThat(lines("070-4512-3387", "문의 help@spo-secure.example", "다시 070 4512 3387로"))
                .containsExactly("[전화1]", "문의 [메일1]", "다시 [전화1]로");
    }

    @Test
    void 직함이나_예금주_앞뒤의_모르는_이름은_인물로_바꾼다() {
        List<String> masked = talk(
                "저는 서울중앙지방검찰청 청장 범죄 수사의 팀 김민준 수사관입니다. 지금 통화 가능 하십니까?",
                "김우리 아까 전관 김민준 수사관입니다. 보안 점검 앱은 설치하셨습니까?",
                "우선 300만 원을 카카오톡으로 보내드린 국민은행 사파리 01 01 33 641 예금주 안민수 계좌로 보내주세요.");

        assertThat(masked.get(0)).isEqualTo("저는 서울중앙지방검찰청 청장 범죄 수사의 팀 [인물1] 수사관입니다. 지금 통화 가능 하십니까?");
        assertThat(masked.get(1)).isEqualTo("[피해자] 아까 전관 [인물1] 수사관입니다. 보안 점검 앱은 설치하셨습니까?");
        // 잘못 읽힌 계좌번호는 아는 계좌와 대조되지 않아 번호 가림이 되돌리지 않는 라벨로 가린다.
        assertThat(masked.get(2)).startsWith("우선 300만 원을").contains("예금주 [인물2] 계좌로").doesNotContain("33");
    }

    @Test
    void 고유식별정보는_지금처럼_되돌리지_않는_라벨로_가린다() {
        assertThat(lines("900512-2123456").get(0)).doesNotContainPattern("\\d");
    }

    @Test
    void 아는_값의_이름표는_글에_없어도_순서대로_먼저_매겨_대응표로_돌려준다() {
        List<MaskLabel> labels = lines(List.of(), "신한은행 110-472-836150").labels();

        assertThat(labels).extracting(MaskLabel::label).containsExactly(
                "[피해자]", "[피해자전화]", "[피해자메일]", "[피해자주소]", "[피해자계좌]",
                "[받은계좌1]", "[예금주1]", "[받은계좌2]", "[예금주2]",
                "[지역1]", "[지역2]", "[지역3]", "[지역4]");
        assertThat(labels).filteredOn(l -> l.label().equals("[받은계좌2]")).singleElement()
                .satisfies(l -> {
                    assertThat(l.kind()).isEqualTo(MaskLabel.Kind.ACCOUNT);
                    assertThat(l.value()).isEqualTo("110-472-836150");
                });
    }

    @Test
    void 이미_매긴_이름표는_그대로_쓰고_새_값은_끝_번호로_붙인다() {
        List<MaskLabel> before = lines(List.of(), "070-4512-3387").labels();

        MaskResult after = lines(before, "02-3480-2000", "070-4512-3387");

        assertThat(after.texts()).containsExactly("[전화2]", "[전화1]");
        assertThat(after.labels()).hasSize(before.size() + 1).startsWith(before.toArray(MaskLabel[]::new));
        assertThat(after.labels().getLast()).isEqualTo(new MaskLabel("[전화2]", MaskLabel.Kind.PHONE, "02-3480-2000"));
    }
}
