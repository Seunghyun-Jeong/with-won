package com.withwon.integration.ai.service;

import com.withwon.integration.ai.dto.CaseKnown;
import com.withwon.integration.ai.dto.MaskInput;
import com.withwon.integration.ai.dto.MaskKnown;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * LLM에 보내기 직전 한 곳에서 고유식별정보와 인증번호를 가린다(직원-7). 가린 것은 되돌리지 않고, 담당자 화면은 가리지 않는다.
 *
 * <p>원칙: 놓치는 것보다 괜히 가리는 쪽을 택한다. 판정은 이 순서다.
 * <ol>
 * <li>비밀 범위를 먼저 정한다. 비밀을 묻는 말(주민번호, OTP 등)이 있는 발화 전체와 그 뒤의 발화들, 줄(서류)이면 같은 문장과 앞뒤 한 줄이다.
 *     값이 이름보다 먼저 나와도("742가 CVC예요") 범위 안이다. 답을 "앞은 482" / "뒤는 619"로 나눠 말하므로
 *     가려진 것 없이 {@value #QUIET}조각이 지나야 범위를 닫는다.</li>
 * <li>날짜, 시각, 금액을 온전한 표현으로 알아보고 남긴다. 비밀 범위에서는 더 좁게 본다(원이 붙은 금액, 시각, 연월일만).
 *     쉼표나 단위 한 글자만으로는 남기지 않는다("738,019", "4826만요", "1004호").
 *     비밀 이름에 바로 붙은 값("비밀번호는 5월 12일이요")과 답이 그것뿐인 발화("5월 12일이요")는 날짜 모양이어도 가린다.</li>
 * <li>남은 숫자 덩어리 가운데 비밀 범위의 것은 모두 가린다. 범위 밖에서는 7자리 이상(주민번호 뒷자리 길이),
 *     한 자리씩 읽은 4자리 이상, 대화 속에 홀로 있는 4~6자리, 한 발화에 단위 없는 숫자가 3개 이상일 때 가린다.
 *     전화번호 형식과 사건의 아는 계좌만 남긴다.</li>
 * </ol>
 *
 * <p>숫자 덩어리는 넓게 묶는다. CLOVA Speech는 "2 1 2 3, 4, 5 6", "7 3 8 0 1 구요"처럼 띄어 쓰고 한글 숫자를 섞고,
 * OCR은 "90|05|12"처럼 칸 구분자를 끼운다. 날짜와 금액은 2번에서 먼저 빼 두었으므로 구분자를 넓게 이어도 다치지 않는다.
 *
 * <p>가린 뒤 재검사에 걸리거나 가리다 오류가 나면 그 조각은 실패(null)이고 LLM에 보내지 않는다.
 * 같은 글로 다시 해도 같으므로 다시 시도하지 않는다(직원-8).
 *
 * <p>막지 못하는 것: 질문도 없고 단위도 없는 3자리 이하 번호, 줄(서류) 속에 홀로 있는 4~6자리 번호,
 * 물건의 위치로만 묻는 말("서명하는 데 끝에 찍힌 거"), STT가 숫자를 다른 낱말로 적은 것("사팔"→"사파"),
 * 금액 모양으로 말한 비밀값("4원, 8원", "금액 482619"). 놓친 수는 진짜 통화로 재야 안다.
 * 답을 나눠 말하다가 가려진 것 없는 발화가 {@value #QUIET}개 끼면 그 뒤의 3자리 이하 답("뒤는 619예요").
 * 괜히 가리는 것: 비밀 이름이 든 발화 안의 다른 날짜와 금액("비번 재설정은 11/26에")은 같이 가려진다.
 */
@Service
public class MaskService {

    // 같은 자리끼리 글자와 값이 짝이다. 령, 륙은 영, 육의 다른 표기.
    private static final String KD = "영공령빵일이삼사오육륙칠팔구";
    private static final String KD_VALUES = "00001234566789";
    private static final String KD_EXCEPT_1_2 = "영공령빵삼사오육륙칠팔구";
    private static final List<String> NATIVE = List.of("하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉");
    private static final String NATIVE_ANY = "(?:" + String.join("|", NATIVE) + ")";
    // 한글 숫자가 낱말이 아니라 숫자로 끝났다고 볼 수 있는 뒷말.
    private static final String EDGE =
            "(?=[^가-힣]|$|이?요|입니다|이에요|예요|에(?:[^가-힣]|$)|이랑|랑(?:[^가-힣]|$)|하고|이고)";
    /*
     * 세 글자 이상 붙여 읽은 한글 숫자는 뒷말과 상관없이 숫자다("칠삼팔공일구거든요").
     * 두 글자는 오이, 사이, 이사 같은 낱말과 겹쳐 약한 근거로만 친다(다른 숫자와 이어질 때만 숫자).
     * 숫자 바로 뒤의 '일'은 날짜("18일")라 뒤에 숫자가 또 붙을 때만, '이'는 "6이요"의 조사가 아닐 때만 숫자로 본다.
     * 십, 백, 천을 넣어 읽은 수("천이백삼십사")는 금액일 수도 있어 비밀 범위에서만 쓴다.
     */
    private static final Pattern TOKEN = Pattern.compile(
            "(?<arabic>\\d+)"
                    + "|(?<![가-힣])(?:"
                    + "(?<wordy>(?=[" + KD + "천백십만]*[천백십])(?:[" + KD + "천백십만]{3,}|[" + KD + "천백십만]{2}" + EDGE + "))"
                    + "|(?<strong>(?!일일이)(?:쌍?[" + KD + "]){3,}|쌍[" + KD + "]" + EDGE
                    + "|(?:[" + KD_EXCEPT_1_2 + "]|일(?=\\d)|(?<!\\d)일|이(?!요|에요|랑))" + EDGE + ")"
                    + "|(?<weak>[" + KD + "]{2}" + EDGE + ")"
                    + "|(?<native>" + NATIVE_ANY + "{2,}|" + NATIVE_ANY + EDGE + "))");
    private static final Pattern ENGLISH = Pattern.compile(
            "(?i)(?<![a-z가-힣])(?:(?:zero|oh|one|two|three|four|five|six|seven|eight|nine|double"
                    + "|제로|쓰리|스리|파이브|식스|세븐|에잇|나인|더블|원|투|포)(?:[\\s,]+|(?![a-z가-힣]))){3,}");
    private static final Pattern JOIN = Pattern.compile(
            "[\\s\\-,./|·:]{0,3}|\\s*(?:다시|대시|하이픈|슬래시|하고|그리고|그\\s?다음|이랑|랑|에)\\s*");
    private static final int LONG_DIGITS = 7;
    private static final int SPELLED_DIGITS = 4;
    private static final int BARE_RUNS = 3;
    private static final int QUIET = 2;
    private static final int WAIT_FOR_ANSWER = 10;
    static final Pattern PHONE = Pattern.compile("0(?:1[016789]|2|[3-6][1-5]|70)\\d{7,8}");
    // 대표번호 8자리는 한 자리씩 부른 인증번호와 길이가 같아, 하이픈으로 적힌 것만 전화번호로 본다.
    private static final Pattern REP_PHONE = Pattern.compile("1[5-9]\\d{2}-\\d{4}");
    private static final Pattern PASSPORT = Pattern.compile(
            "(?<![A-Za-z0-9])(?:[A-Z]{1,2}\\d{7,8}|[A-Z]\\d{3}[A-Z]\\d{4})(?!\\d)");
    private static final Pattern ALNUM = Pattern.compile(
            "[A-Za-z0-9!@#$%^&*_]*(?:[A-Za-z][A-Za-z!@#$%^&*_]*\\d|\\d[\\d!@#$%^&*_]*[A-Za-z])[A-Za-z0-9!@#$%^&*_]*");
    private static final Pattern SENTENCE_END = Pattern.compile("[.?!](?=\\s|$)");
    private static final String DEFAULT_LABEL = "[가린 번호]";
    private static final String RRN_LABEL = "[주민번호]";
    private static final String DASHES = "‐‑‒–—―−－﹣";

    // 비밀 범위에서도 남기는 표현: 시각, 연월일, 원이 붙은 금액.
    private static final List<Pattern> KEEP_ALWAYS = patterns(
            "(?<![\\d:])(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?![\\d:])",
            // 뒤에 숫자가 바로 붙으면 날짜가 아니라 섞어 쓴 번호다("2일2삼4").
            "(?<!\\d)\\d{1,2}(?:시|분|초|년|월|일|개월)(?!\\d)",
            "(?<!\\d)(?:(?:19|20)\\d{2}|\\d{2})년",
            "(?<!\\d)(?:19|20)\\d{2}[\\s.\\-/]{1,2}(?:0?[1-9]|1[0-2])[\\s.\\-/]{1,2}(?:0?[1-9]|[12]\\d|3[01])(?!\\d)",
            "₩\\s?\\d[\\d,]*",
            "\\d[\\d,]*\\s?(?:억|천만|백만|십만|만|천|백)?\\s?원",
            "(?<![가-힣])[일이삼사오육륙칠팔구천백십만억]{2,}\\s?원");
    // 묻는 말 속의 자릿수("OTP 번호 6자리"). 비밀 이름 바로 뒤에 오지만 값이 아니다.
    private static final List<Pattern> KEEP_PLACES = patterns("(?<!\\d)\\d{1,2}자리");
    // 비밀 범위 밖에서만 남기는 표현: 원 없는 금액, 연-월.
    private static final List<Pattern> KEEP_OUTSIDE = patterns(
            "\\d[\\d,]*(?:억|천만|백만|십만|만)",
            "(?:금액|잔액|송금액|이체액|피해액)\\D{0,3}\\d+",
            "(?<!\\d)(?:19|20)\\d{2}[.\\-/](?:0?[1-9]|1[0-2])(?![\\d.\\-/])");
    // 범위 밖에서 남기되, 발화가 그것뿐이면 질문이 안 읽힌 답일 수 있어 가린다("20261126입니다", "4826호입니다").
    private static final List<Pattern> KEEP_UNLESS_ALONE = patterns(
            "(?<![\\d/.])(?:0?[1-9]|1[0-2])[/.](?:0?[1-9]|[12]\\d|3[01])(?![\\d/.])",
            "(?<!\\d)(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(?!\\d)",
            "(?<!\\d)\\d{1,4}(?:호|동|층|명|개|건|회|차|번지|%|퍼센트|팀|대|살|세)");
    // 원 없는 쉼표 금액은 서류의 표에서만 남긴다. 대화에서는 "482,619"가 인증번호일 수 있다.
    private static final List<Pattern> KEEP_IN_LINES = patterns("(?<![\\d,])\\d{1,3}(?:,\\d{3}){1,3}(?![\\d,])");
    // 비밀 이름과 값 사이, 값 뒤에 올 수 있는 말. "인증번호는 12:34로"는 값이고 "인증번호는 3분 안에"는 아니다.
    private static final Pattern DIRECT_GAP = Pattern.compile("\\s?(?:번호)?\\s?(?:[은는이가도:=]|은요|는요)?\\s*");
    private static final Pattern DIRECT_TAIL = Pattern.compile(
            "(?:(?:입니다|이에요|예요|이요|요|이고|라고|으?로|이야|야|임)(?:[\\s.,!?]|$)|[.,!?]|$)");

    private static List<Pattern> patterns(String... regexes) {
        return Arrays.stream(regexes).map(Pattern::compile).toList();
    }

    /** 낱말 사이에 OCR이 끼운 띄어쓰기를 허용한다("카 드 번 호"). */
    private static String spaced(String... words) {
        return Arrays.stream(words)
                .map(w -> w.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining("\\s?")))
                .collect(Collectors.joining("|"));
    }

    private static final int LOOSE = 0;
    private static final int GENERIC = 1;
    private static final int SPECIFIC = 2;

    /**
     * rank가 높을수록 그 조각을 대표한다. SPECIFIC은 비밀 이름, GENERIC은 이름 없이 화면의 숫자나 코드를 달라는 말,
     * LOOSE는 "번호 불러 주세요"처럼 전화번호를 묻는 것일 수도 있는 말이다. LOOSE 뒤에서만 전화번호 형식을 남긴다
     * (전화번호를 비밀번호로 쓰는 사람이 있다).
     */
    private record Ask(Pattern pattern, String label, int rank) {
        Ask(String regex, String label) {
            this(Pattern.compile(regex), label, SPECIFIC);
        }
    }

    private static final String ASK_VERB = "(?:불러|말씀|말해|얘기해|대답해|알려|입력|눌러|읽어|적어|보내|전달|찍어|읊어"
            + "|어떻게\\s?[되돼]|몇\\s?번|뭐라고\\s?(?:떠|나와))";
    private static final List<Ask> ASKS = List.of(
            new Ask(spaced("외국인등록", "외국인번호", "등록증번호", "거소신고") + "|(?i:(?<![a-z])arc(?![a-z]))", "[외국인등록번호]"),
            // "주민센터"는 질문이 아니다.
            new Ask("주\\s?민\\s?(?:등\\s?록\\s?)?번\\s?호|주\\s?민\\s?등\\s?록(?!\\s?증)|민\\s?증", RRN_LABEL),
            new Ask(spaced("비밀번호", "비번", "패스워드", "패스코드", "암호", "핀번호", "핀코드", "핀넘버", "잠금번호")
                    + "|(?i:(?<![a-z])pin(?![a-z])|password|passcode)", "[비밀번호]"),
            // CLOVA Speech가 "오티비", "오피티", "o t p"로 읽을 수 있다.
            new Ask("(?i:o\\.?\\s?t\\.?\\s?p)|오\\s?티\\s?[피비]|" + spaced("오디피", "오피티", "일회용", "발생기", "생성기"), "[OTP]"),
            new Ask(spaced("인증번호", "인증코드", "인증값", "인증키", "인중번호", "승인번호", "확인코드", "확인번호", "보안코드",
                    "검증코드", "접속코드", "로그인코드"), "[인증번호]"),
            new Ask(spaced("보안카드", "보안카트", "보안표", "코드표", "숫자표", "난수표"), "[보안카드번호]"),
            new Ask("(?<!보안\\s?)카\\s?드\\s?번\\s?호|" + spaced("유효기간", "유효기한", "유효기관", "만료", "서명란", "카드검증",
                    "카드보안", "씨브이씨", "시브이시", "씨브이브이", "시브이브이")
                    + "|(?i:cv[cv]2?|(?<![a-z])cid(?![a-z])|(?<![a-z])exp(?:iry)?(?![a-z])|valid\\s?thr(?:u|ough)|mm/yy)",
                    "[카드번호]"),
            new Ask(spaced("면허번호", "면허증", "운전면허"), "[운전면허번호]"),
            new Ask(spaced("여권번호") + "|(?i:passport)", "[여권번호]"),
            // 질문 단어가 없는 요구("앱에 뜬 번호 불러 주세요", "말해 봐요, 화면에 뜬 숫자"). 무엇을 묻는지 몰라 표시는 기본으로 둔다.
            new Ask(generic("(?:숫자|코드|뜬\\s?[거걸]|찍힌\\s?[거걸]|적힌\\s?[거걸]|나온\\s?[거걸])"), DEFAULT_LABEL, GENERIC),
            new Ask(generic("(?:번호|자리)"), DEFAULT_LABEL, LOOSE));

    private static Pattern generic(String object) {
        return Pattern.compile(object + "[^.?!]{0,25}" + ASK_VERB + "|" + ASK_VERB + "[^.?!]{0,15}" + object);
    }
    private static final Pattern LABEL = Pattern.compile("\\[(?:[가-힣A-Z ]+)]");

    private record Found(int start, int end, String label, int rank) {
        boolean strict() {
            return rank > LOOSE;
        }
    }

    /** 가릴 범위. strict면 전화번호 형식도 가리고 남기는 표현도 좁게 본다. */
    private record Scope(String label, boolean strict) {
    }

    private enum Kind { ARABIC, STRONG, WEAK, WORDY }

    private record Token(int start, int end, String digits, Kind kind) {
    }

    /** 이어진 숫자 덩어리. arabicDigits는 전화번호와 아는 계좌 대조용(뒤에 붙은 낱말 '이' 같은 한글 숫자는 뺀다). */
    private record Run(int start, int end, String digits, String arabicDigits, int spelledDigits, int weakTokens,
            int tokens) {
    }

    private record Span(int start, int end, String label) {
    }

    /**
     * LLM에 보내기 직전의 한 입구. 번호를 가린 뒤 사람, 번호, 주소를 사건의 이름표로 바꾼다(Pseudonymizer).
     * existing은 이 사건에 이미 매긴 대응표(mask_label)이고, 돌려주는 labels는 거기에 새로 매긴 것을 끝에 붙인 전체다.
     */
    public MaskResult maskForLlm(List<MaskInput> fragments, CaseKnown known, List<MaskLabel> existing) {
        Pseudonymizer names = new Pseudonymizer(known, existing);
        List<String> texts = mask(fragments, known.maskKnown()).stream()
                .map(t -> t == null ? null : names.apply(t))
                .toList();
        return new MaskResult(texts, names.labels());
    }

    /** 한 서류(녹음)의 조각을 순서대로 받아 같은 순서로 가린 글을 돌려준다. 가림에 실패한 조각은 null. */
    public List<String> mask(List<MaskInput> fragments, MaskKnown known) {
        Set<String> keep = new HashSet<>();
        known.keepAccounts().forEach(a -> keep.add(a.replaceAll("\\D", "")));
        List<String> secrets = secrets(known.rrns());

        List<String> result = new ArrayList<>();
        String asker = null;
        Scope answer = null;
        int sinceAsk = 0;
        boolean answered = false;
        int quiet = 0;
        Scope nextLine = null;
        for (int i = 0; i < fragments.size(); i++) {
            String original = fragments.get(i).text();
            String speaker = fragments.get(i).speaker();
            Scope context = null;
            if (speaker == null) {
                context = nextLine;
                asker = null;
            } else if (asker != null) {
                sinceAsk++;
                if (answered ? quiet >= QUIET : sinceAsk > WAIT_FOR_ANSWER) {
                    asker = null;
                } else {
                    // 화자 구분이 틀려 답이 질문자 발화로 묶였을 수 있다. 누구 번호인지는 모르므로 질문 표시는 붙이지 않는다.
                    context = speaker.equals(asker) ? new Scope(DEFAULT_LABEL, false) : answer;
                }
            }
            nextLine = null;

            String masked = maskAndCheck(original, speaker != null, context, keep, secrets);
            result.add(masked);
            if (context != null && speaker != null) {
                answered |= !original.equals(masked);
                quiet = original.equals(masked) ? quiet + 1 : 0;
            }

            String text = normalize(original);
            Found ask = lastAsk(text);
            // 답하는 쪽이 "인증번호 말씀이세요?"라고 되물은 것은 새 질문이 아니다.
            boolean echo = asker != null && !answered && speaker != null && !speaker.equals(asker);
            if (ask != null && speaker != null && !echo) {
                asker = speaker;
                answer = new Scope(ask.label(), ask.strict());
                sinceAsk = 0;
                answered = false;
                quiet = 0;
            } else if (ask != null && speaker == null && runs(text, false, List.of(), null, at -> null).isEmpty()) {
                // 양식의 "주민등록번호" 칸 이름, 문자 캡처의 "인증번호" 줄처럼 값이 앞뒤 줄에 있다(OCR 읽는 순서는 믿을 수 없다).
                nextLine = new Scope(ask.label(), ask.strict());
                if (i > 0 && fragments.get(i - 1).speaker() == null) {
                    result.set(i - 1, maskAndCheck(fragments.get(i - 1).text(), false, nextLine, keep, secrets));
                }
            }
        }
        nullIfSplitSecret(fragments, result, secrets);
        return result;
    }

    private static String maskAndCheck(String original, boolean spoken, Scope context, Set<String> keep,
            List<String> secrets) {
        try {
            String masked = maskOne(original, spoken, context, keep);
            // 가린 글을 같은 조건으로 다시 가려도 그대로여야 한다. 달라지면 가릴 것이 남았다는 뜻이다.
            boolean stable = masked.equals(maskOne(masked, spoken, context, keep));
            return stable && !leaks(masked, secrets, keep) ? masked : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String maskOne(String original, boolean spoken, Scope context, Set<String> keep) {
        String text = normalize(original);
        List<Found> asks = asks(text);
        IntFunction<Scope> scopeAt = at -> {
            Found ask = askNear(text, spoken, asks, at);
            return ask != null ? new Scope(ask.label(), ask.strict()) : context;
        };

        List<Span> spans = new ArrayList<>();
        Matcher passport = PASSPORT.matcher(text);
        while (passport.find()) {
            spans.add(new Span(passport.start(), passport.end(), "[여권번호]"));
        }
        Matcher alnum = ALNUM.matcher(text);
        while (alnum.find()) {
            Scope scope = scopeAt.apply(alnum.start());
            if (scope != null && scope.strict() && free(spans, alnum.start(), alnum.end())) {
                spans.add(new Span(alnum.start(), alnum.end(), scope.label()));
            }
        }

        List<Run> runs = runs(text, spoken, asks, spans, scopeAt);
        long bare = runs.stream().filter(r -> scopeAt.apply(r.start()) == null).count();
        for (Run run : runs) {
            boolean direct = asks.stream().anyMatch(ask -> ask.strict() && ask.end() <= run.start()
                    && DIRECT_GAP.matcher(text.substring(ask.end(), run.start())).matches());
            String label = label(text, spoken, run, scopeAt.apply(run.start()), bare, direct ? Set.of() : keep);
            if (label != null) {
                spans.add(new Span(run.start(), run.end(), label));
            }
        }

        spans.sort(Comparator.comparingInt(Span::start));
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (Span span : spans) {
            out.append(original, last, span.start()).append(span.label());
            last = span.end();
        }
        return out.append(original.substring(last)).toString();
    }

    private static String label(String text, boolean spoken, Run run, Scope scope, long bareRuns, Set<String> keep) {
        if (keep.contains(run.arabicDigits())) {
            return null;
        }
        String phoneDigits = run.start() > 0 && text.charAt(run.start() - 1) == '+' && run.arabicDigits().startsWith("82")
                ? "0" + run.arabicDigits().substring(2) : run.arabicDigits();
        boolean phone = run.digits().equals(run.arabicDigits()) && (PHONE.matcher(phoneDigits).matches()
                || REP_PHONE.matcher(text.substring(run.start(), run.end())).matches());
        if (scope != null) {
            return phone && !scope.strict() ? null : scope.label();
        }
        if (phone) {
            return null;
        }
        int digits = run.digits().length();
        // 두 글자 한글 숫자는 낱말일 수 있어("사이 일이") 셋 이상 이어질 때만 읽은 숫자로 친다.
        boolean spelled = run.spelledDigits() >= SPELLED_DIGITS && (run.weakTokens() == 0 || run.tokens() >= 3);
        boolean aloneInTalk = spoken && !run.arabicDigits().isEmpty() && digits >= SPELLED_DIGITS;
        if (digits >= LONG_DIGITS || spelled || aloneInTalk || spoken && bareRuns >= BARE_RUNS) {
            return rrnShaped(run.digits()) ? RRN_LABEL : DEFAULT_LABEL;
        }
        return null;
    }

    /**
     * 이 위치가 속한 질문. 발화와 메시지는 조각 안 어디든(값이 이름보다 먼저 와도), 줄은 같은 문장 안에서만 본다
     * (경위 글의 다른 문장에 있는 번호까지 가리지 않으려고). 비밀을 묻는 말이 일반 질문보다 먼저다.
     */
    private static Found askNear(String text, boolean spoken, List<Found> asks, int at) {
        Found near = null;
        for (Found ask : asks) {
            int from = Math.min(ask.end(), at);
            int to = Math.max(ask.start(), at);
            if (spoken || from >= to || !SENTENCE_END.matcher(text.substring(from, to)).find()) {
                near = ask;
            }
        }
        return near;
    }

    /** rank가 높은 질문이 뒤에 오도록 정렬한다. 마지막 것이 그 조각을 대표한다. */
    private static List<Found> asks(String text) {
        // 가린 자리의 표시("[주민번호]")를 질문으로 다시 읽지 않는다.
        String plain = LABEL.matcher(text).replaceAll(r -> "□".repeat(r.group().length()));
        List<Found> found = new ArrayList<>();
        for (Ask ask : ASKS) {
            Matcher m = ask.pattern().matcher(plain);
            while (m.find()) {
                found.add(new Found(m.start(), m.end(), ask.label(), ask.rank()));
            }
        }
        found.sort(Comparator.comparing(Found::rank).thenComparing(Found::start));
        return found;
    }

    private static Found lastAsk(String text) {
        List<Found> asks = asks(text);
        return asks.isEmpty() ? null : asks.getLast();
    }

    private static boolean free(List<Span> spans, int start, int end) {
        return spans.stream().noneMatch(s -> s.start() < end && start < s.end());
    }

    /** 남기는 표현과 이미 가릴 자리를 뺀 숫자 덩어리. spans가 null이면 가릴 자리 없이 센다(줄에 값이 있는지만 볼 때). */
    private static List<Run> runs(String text, boolean spoken, List<Found> asks, List<Span> spans,
            IntFunction<Scope> scopeAt) {
        List<int[]> always = matches(KEEP_ALWAYS, text);
        always.sort(Comparator.comparingInt(r -> r[0]));
        List<int[]> values = valuesOfSecret(text, asks, always);
        boolean onlyKept = rest(text, always).isEmpty();
        List<int[]> places = matches(KEEP_PLACES, text);
        List<int[]> outside = matches(KEEP_OUTSIDE, text);
        List<int[]> unlessAlone = matches(KEEP_UNLESS_ALONE, text);
        List<int[]> inLines = matches(KEEP_IN_LINES, text);

        List<Run> runs = new ArrayList<>();
        List<Token> joined = new ArrayList<>();
        for (Token token : tokens(text)) {
            Scope scope = scopeAt.apply(token.start());
            boolean strict = scope != null && scope.strict();
            int[] kept = around(always, token);
            boolean keepIt = around(places, token) != null
                    || kept != null && !(strict && (onlyKept || values.contains(kept)));
            if (!keepIt && !strict) {
                int[] alone = around(unlessAlone, token);
                keepIt = around(outside, token) != null || !spoken && around(inLines, token) != null
                        || alone != null && !(spoken && rest(text, List.of(alone)).isEmpty());
            }
            if (keepIt || token.kind() == Kind.WORDY && !strict
                    || spans != null && !free(spans, token.start(), token.end())) {
                continue;
            }
            if (!joined.isEmpty() && !JOIN.matcher(text.substring(joined.getLast().end(), token.start())).matches()) {
                addIfNumber(runs, joined, text, scopeAt);
                joined.clear();
            }
            joined.add(token);
        }
        addIfNumber(runs, joined, text, scopeAt);
        return runs;
    }

    /** 남기는 표현 가운데 비밀 이름에 바로 붙어 그 값으로 쓰인 것("비밀번호는 5월 12일이요"의 5월과 12일). */
    private static List<int[]> valuesOfSecret(String text, List<Found> asks, List<int[]> always) {
        List<int[]> values = new ArrayList<>();
        for (int i = 0; i < always.size(); i++) {
            int start = always.get(i)[0];
            if (asks.stream().noneMatch(ask -> ask.strict() && ask.end() <= start
                    && DIRECT_GAP.matcher(text.substring(ask.end(), start)).matches())) {
                continue;
            }
            int last = i;
            while (last + 1 < always.size()
                    && text.substring(always.get(last)[1], always.get(last + 1)[0]).isBlank()) {
                last++;
            }
            if (DIRECT_TAIL.matcher(text).region(always.get(last)[1], text.length()).lookingAt()) {
                values.addAll(always.subList(i, last + 1));
            }
        }
        return values;
    }

    private static void addIfNumber(List<Run> runs, List<Token> tokens, String text,
            IntFunction<Scope> scopeAt) {
        if (tokens.isEmpty()) {
            return;
        }
        StringBuilder digits = new StringBuilder();
        StringBuilder arabic = new StringBuilder();
        int spelled = 0;
        int weak = 0;
        boolean strong = false;
        for (Token token : tokens) {
            digits.append(token.digits());
            if (token.kind() == Kind.ARABIC) {
                arabic.append(token.digits());
            }
            if (token.kind() != Kind.ARABIC || token.digits().length() == 1) {
                spelled += token.digits().length();
            }
            if (token.kind() == Kind.WEAK || token.kind() == Kind.WORDY && token.digits().length() == 2) {
                weak++;
            } else if (token.kind() != Kind.ARABIC) {
                strong = true;
            }
        }
        Run run = new Run(tokens.getFirst().start(), tokens.getLast().end(), digits.toString(), arabic.toString(),
                spelled, weak, tokens.size());
        // 한글 숫자 하나("이 통화"의 '이')나 두 글자 낱말 둘("사이 일이")은 숫자가 아닐 수 있다.
        // 비밀 질문의 답이 그것뿐일 때("칠삼", "비밀번호는 이사 이사예요")만 숫자로 본다.
        Scope scope = scopeAt.apply(run.start());
        boolean onlyAnswer = scope != null && scope.strict()
                && rest(text, List.of(new int[] {run.start(), run.end()})).isEmpty();
        if (arabic.length() > 0 || strong && (tokens.size() >= 2 || digits.length() >= 3) || weak >= 3
                || onlyAnswer) {
            runs.add(run);
        }
    }

    /** 주어진 자리와 질문 단어, 맞장구와 조사를 뺀 나머지 글. 비어 있으면 그 발화는 그 값뿐이다. */
    private static String rest(String text, List<int[]> ranges) {
        StringBuilder rest = new StringBuilder(text);
        ranges.forEach(r -> rest.replace(r[0], r[1], " ".repeat(r[1] - r[0])));
        for (Found ask : asks(text)) {
            rest.replace(ask.start(), ask.end(), " ".repeat(ask.end() - ask.start()));
        }
        return rest.toString().replaceAll("[^가-힣A-Za-z0-9]", " ").strip()
                .replaceAll("^(?:네|예|아)\\s|(?:^|\\s)(?:[은는이가도]|이?요|예요|이에요|입니다)(?=\\s|$)", "").strip();
    }

    private static List<int[]> matches(List<Pattern> patterns, String text) {
        List<int[]> found = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                found.add(new int[] {m.start(), m.end()});
            }
        }
        return found;
    }

    private static int[] around(List<int[]> ranges, Token token) {
        return ranges.stream().filter(r -> r[0] <= token.start() && token.end() <= r[1]).findFirst().orElse(null);
    }

    private static List<Token> tokens(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher english = ENGLISH.matcher(text);
        while (english.find()) {
            String words = english.group().strip();
            // 값은 재검사의 아는 주민번호 대조에만 쓰이고, 영어로 읽은 번호는 길이만 알면 가릴 수 있다.
            tokens.add(new Token(english.start(), english.start() + words.length(),
                    "0".repeat(words.split("[\\s,]+").length), Kind.STRONG));
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            int start = m.start();
            if (tokens.stream().anyMatch(t -> t.start() <= start && start < t.end())) {
                continue;
            }
            if (m.group("arabic") != null) {
                tokens.add(new Token(start, m.end(), m.group(), Kind.ARABIC));
            } else if (m.group("wordy") != null) {
                tokens.add(new Token(start, m.end(), "0".repeat(m.group().length()), Kind.WORDY));
            } else if (m.group("native") != null) {
                StringBuilder digits = new StringBuilder();
                Matcher n = Pattern.compile(NATIVE_ANY).matcher(m.group());
                while (n.find()) {
                    digits.append(NATIVE.indexOf(n.group()) + 1);
                }
                tokens.add(new Token(start, m.end(), digits.toString(), Kind.STRONG));
            } else {
                StringBuilder digits = new StringBuilder();
                String korean = m.group().replace("쌍", "");
                korean.chars().forEach(c -> digits.append(KD_VALUES.charAt(KD.indexOf(c))));
                tokens.add(new Token(start, m.end(), digits + (m.group().contains("쌍") ? "0" : ""),
                        m.group("weak") != null ? Kind.WEAK : Kind.STRONG));
            }
        }
        tokens.sort(Comparator.comparingInt(Token::start));
        return tokens;
    }

    /**
     * 전각 숫자와 동그라미 숫자, 여러 모양의 대시와 공백(제로폭 포함), 숫자 옆의 O와 I(OCR이 0과 1을 잘못 읽은 것)를
     * 같은 글자로 바꾼다. 글자 수가 그대로라 위치를 원문에 그대로 쓴다.
     */
    private static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean nextToDigit = i > 0 && Character.isDigit(out.charAt(i - 1))
                    || i + 1 < text.length() && Character.isDigit(text.charAt(i + 1));
            int value = Character.getNumericValue(c);
            if (Character.isDigit(c) || Character.getType(c) == Character.OTHER_NUMBER && value >= 0 && value <= 9) {
                out.append((char) ('0' + value));
            } else if (nextToDigit && "OoIl".indexOf(c) >= 0) {
                out.append(c == 'O' || c == 'o' ? '0' : '1');
            } else if (DASHES.indexOf(c) >= 0) {
                out.append('-');
            } else {
                out.append(Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT ? ' ' : c);
            }
        }
        return out.toString();
    }

    private static String digitsOf(String text) {
        StringBuilder digits = new StringBuilder();
        tokens(normalize(text)).forEach(t -> digits.append(t.digits()));
        return digits.toString();
    }

    /** 주민번호 전체와, 녹음에서 따로 불리는 뒷자리 7자리. */
    private static List<String> secrets(List<String> rrns) {
        List<String> secrets = new ArrayList<>();
        for (String rrn : rrns) {
            String digits = rrn.replaceAll("\\D", "");
            secrets.add(digits);
            if (digits.length() == 13) {
                secrets.add(digits.substring(6));
            }
        }
        return secrets;
    }

    /** 가림 규칙과 따로 하는 재검사: 아는 주민번호, 구분자를 걷어 낸 숫자 줄이 주민번호나 카드번호 모양인지. */
    private static boolean leaks(String masked, List<String> secrets, Set<String> keep) {
        String digits = digitsOf(masked);
        if (secrets.stream().anyMatch(digits::contains)) {
            return true;
        }
        // 날짜, 시각, 금액까지 이어 붙이면 정상 거래 줄("2026-11-26 14:32 4,829")이 카드번호로 보인다.
        StringBuilder text = new StringBuilder(normalize(masked));
        for (List<Pattern> kept : List.of(KEEP_ALWAYS, KEEP_IN_LINES)) {
            matches(kept, text.toString()).forEach(r -> text.replace(r[0], r[1], "□".repeat(r[1] - r[0])));
        }
        Matcher chain = Pattern.compile("\\d(?:[\\s./|·:,\\-]{0,3}\\d)+").matcher(text);
        while (chain.find()) {
            String joined = chain.group().replaceAll("\\D", "");
            if (!keep.contains(joined) && (rrnShaped(joined) || cardShaped(joined))) {
                return true;
            }
        }
        return false;
    }

    private static boolean cardShaped(String digits) {
        return (digits.length() == 15 || digits.length() == 16) && luhn(digits);
    }

    private static boolean rrnShaped(String digits) {
        return digits.matches("\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])[1-8]\\d{6}");
    }

    /**
     * 조각 하나만 봐서는 모르는 누출. 아는 주민번호가 이웃한 조각들에 나뉜 것("뒷자리 212" / "3456")과,
     * 숫자뿐인 줄이 이어져 합치면 주민번호나 카드번호 모양인 것, 양식 칸을 한 글자씩 읽어 한두 자리 줄이 6개 이상
     * 이어진 것은 모두 실패로 돌린다. 1, 2, 3…으로 느는 표의 순번 열은 번호가 아니다.
     */
    private static void nullIfSplitSecret(List<MaskInput> fragments, List<String> result, List<String> secrets) {
        List<String> digits = result.stream().map(m -> m == null ? "" : digitsOf(m)).toList();
        for (int i = 0; i < digits.size(); i++) {
            StringBuilder joined = new StringBuilder(digits.get(i));
            for (int j = i + 1; j < Math.min(i + 6, digits.size()); j++) {
                joined.append(digits.get(j));
                if (secrets.stream().anyMatch(joined.toString()::contains)) {
                    for (int k = i; k <= j; k++) {
                        result.set(k, null);
                    }
                    break;
                }
            }
        }
        int from = 0;
        for (int i = 0; i <= result.size(); i++) {
            if (i < result.size() && fragments.get(i).speaker() == null
                    && normalize(fragments.get(i).text()).strip().matches("\\d{1,4}")) {
                continue;
            }
            List<String> cells = fragments.subList(from, i).stream().map(f -> normalize(f.text()).strip()).toList();
            String joined = String.join("", cells);
            boolean counting = true;
            for (int c = 1; c < cells.size(); c++) {
                counting &= Integer.parseInt(cells.get(c)) == Integer.parseInt(cells.get(c - 1)) + 1;
            }
            boolean spelledOut = cells.size() >= 6 && cells.stream().allMatch(c -> c.length() <= 2) && !counting;
            if (rrnShaped(joined) || cardShaped(joined) || spelledOut) {
                for (int j = from; j < i; j++) {
                    result.set(j, null);
                }
            }
            from = i + 1;
        }
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            }
            sum += d;
        }
        return sum % 10 == 0;
    }
}
