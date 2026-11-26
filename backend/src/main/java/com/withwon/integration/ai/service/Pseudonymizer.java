package com.withwon.integration.ai.service;

import com.withwon.integration.ai.dto.CaseKnown;
import com.withwon.integration.ai.dto.CaseKnown.Payee;
import com.withwon.integration.ai.dto.MaskLabel;
import com.withwon.integration.ai.dto.MaskLabel.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 번호 가림이 남긴 사람, 전화번호, 계좌번호, 메일, 주소와 지역을 사건의 역할 이름표로 바꾼다(직원-7, 11-26).
 * 이름표는 사건마다 새로 매기고 사건을 넘어 같은 사람을 가리키지 않는다. 대응표는 은행 안에만 두고 LLM에는 이름표만 간다.
 *
 * <p>번호 가림(MaskService.mask) 뒤에 돈다. 먼저 돌면 비밀 범위에서 [계좌1]의 숫자가 다시 가려지고,
 * 번호 가림의 "전화번호 형식과 아는 계좌는 남긴다"는 판정도 바뀐다. 번호 가림이 되돌리지 않는 라벨로 가린 것은 건드리지 않는다.
 *
 * <p>번호 순서: 아는 값을 글에 나오지 않아도 먼저 매긴다(피해자, 원장 시각순 받은 계좌와 예금주, 주소 낱말, 지점).
 * 그래서 [받은계좌2]는 언제나 두 번째 피해 송금을 받은 계좌다. 글에서 새로 찾은 값은 조각 순서대로 끝 번호를 붙이고,
 * 한 번 매긴 번호는 보완 서류가 와도 바꾸지 않는다. 역할을 코드가 모르는 이름은 [인물n]이고 누가 사기범인지는 LLM이 문맥으로 본다.
 *
 * <p>못 가리는 것(최선까지만): 직함이나 "예금주", "저는 ~입니다" 없이 나온 모르는 이름, 두 글자나 네 글자 이름,
 * 기관처럼 청, 국, 팀, 실, 단, 처, 부, 과, 원으로 끝나는 이름(이정원), STT가 다르게 적은 이름("김우리 씨"→"이 씨"),
 * 풀어 쓴 사정("왕십리역 앞 카페"의 역 이름처럼 주소와 지점에 없는 지명), 글자로 읽은 전화번호(번호 가림이 되돌리지 않고 가림).
 * 성을 뺀 이름은 씨, 님, 고객이 붙을 때만 바꾼다(김우리의 "우리"는 우리은행과 겹친다).
 * 괜히 가리는 것: 직함 앞의 세 글자 낱말("금융팀 과장"은 막지만 "보안관 팀장" 같은 것), 지역 낱말이 든 다른 낱말.
 */
final class Pseudonymizer {

    private static final String SURNAME = "[김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노하곽성차주우구민진나지엄채원천방"
            + "공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예봉]";
    private static final String NAME = "(?<![가-힣])(" + SURNAME + "[가-힣]{2}(?<![청국팀실단처부과원]))";
    private static final Pattern UNKNOWN_NAME = Pattern.compile(
            NAME + "(?=\\s?(?:수사관|검사|검찰관|팀장|과장|대리|주임|계장|실장|부장|형사|경위|경감|경사|조사관|사무관))"
                    + "|(?<=예금주|명의자|명의인)\\s?:?\\s?" + NAME + "(?![가-힣])"
                    + "|(?<=저는\\s)" + NAME + "(?=입니다|이에요|예요|이라고|라고)");
    private static final Pattern PHONE_LIKE = Pattern.compile(
            "(?<![\\d-])(?:\\+82[\\s-]?0?|0)\\d{1,2}[\\s-]?\\d{3,4}[\\s-]?\\d{4}(?![\\d-])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final String HONORIFIC = "(?=\\s?(?:씨|님|고객))";
    private static final Pattern REGION_SUFFIX = Pattern.compile("(?:특별시|광역시|특별자치시|특별자치도|시|도|군|구|읍|면|동|로|길|아파트|빌라|오피스텔)$");
    private static final Pattern BRANCH_SUFFIX = Pattern.compile("(?:지점|출장소|점)$");

    private final List<MaskLabel> labels = new ArrayList<>();
    private final Map<String, MaskLabel> byKey = new HashMap<>();

    /** 찾은 자리. label이 있으면 대응표에 이미 있는 값이고, 없으면 kind와 value로 새로 매긴다. */
    private record Hit(int start, int end, MaskLabel label, Kind kind, String value) {
    }

    Pseudonymizer(CaseKnown known, List<MaskLabel> existing) {
        existing.forEach(this::add);
        register(Kind.PERSON, "피해자", known.victimName());
        register(Kind.PHONE, "피해자전화", known.victimPhone());
        register(Kind.EMAIL, "피해자메일", known.victimEmail());
        register(Kind.ADDRESS, "피해자주소", known.victimAddress());
        known.victimAccounts().forEach(a -> register(Kind.ACCOUNT, "피해자계좌", a));
        for (Payee payee : known.payees()) {
            register(Kind.ACCOUNT, "받은계좌", payee.account());
            register(Kind.PERSON, "예금주", payee.holder());
        }
        regionWords(known).forEach(w -> register(Kind.REGION, "지역", w));
    }

    List<MaskLabel> labels() {
        return List.copyOf(labels);
    }

    /** 이름표로 바꾼 글. 바꾼 글에서 또 바꿀 것이 보이거나 오류가 나면 null(LLM에 보내지 않음). */
    String apply(String text) {
        try {
            String out = replace(text);
            return hits(out).isEmpty() ? out : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String replace(String text) {
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (Hit hit : hits(text)) {
            MaskLabel label = hit.label() != null ? hit.label() : labelFor(hit.kind(), hit.value());
            out.append(text, last, hit.start()).append(label.label());
            last = hit.end();
        }
        return out.append(text.substring(last)).toString();
    }

    /** 겹치지 않는 자리를 앞에서부터. 같은 곳에서 시작하면 긴 것, 길이도 같으면 대응표에 있는 것이 이긴다. */
    private List<Hit> hits(String text) {
        List<Hit> all = new ArrayList<>();
        for (MaskLabel label : labels) {
            for (Pattern pattern : patterns(label)) {
                Matcher m = pattern.matcher(text);
                while (m.find()) {
                    all.add(new Hit(m.start(), m.end(), label, null, null));
                }
            }
        }
        Matcher phone = PHONE_LIKE.matcher(text);
        while (phone.find()) {
            if (MaskService.PHONE.matcher(key(Kind.PHONE, phone.group())).matches()) {
                all.add(new Hit(phone.start(), phone.end(), null, Kind.PHONE, phone.group()));
            }
        }
        Matcher email = EMAIL.matcher(text);
        while (email.find()) {
            all.add(new Hit(email.start(), email.end(), null, Kind.EMAIL, email.group()));
        }
        Matcher name = UNKNOWN_NAME.matcher(text);
        while (name.find()) {
            int group = name.start(1) >= 0 ? 1 : name.start(2) >= 0 ? 2 : 3;
            all.add(new Hit(name.start(group), name.end(group), null, Kind.PERSON, name.group(group)));
        }
        all.sort(Comparator.comparingInt(Hit::start)
                .thenComparing(Comparator.comparingInt((Hit h) -> h.end() - h.start()).reversed())
                .thenComparing(h -> h.label() == null));
        List<Hit> kept = new ArrayList<>();
        int end = 0;
        for (Hit hit : all) {
            if (hit.start() >= end) {
                kept.add(hit);
                end = hit.end();
            }
        }
        return kept;
    }

    /** 대응표의 값이 글에 나올 수 있는 모양. 번호는 숫자 사이의 하이픈과 띄어쓰기를 허용한다. */
    private static List<Pattern> patterns(MaskLabel label) {
        String value = label.value();
        return switch (label.kind()) {
            case ACCOUNT, PHONE -> List.of(Pattern.compile("(?<!\\d)" + value.replaceAll("\\D", "")
                    .chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining("[\\s-]?")) + "(?!\\d)"));
            case EMAIL -> List.of(Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE));
            case ADDRESS -> List.of(Pattern.compile(Arrays.stream(value.trim().split("\\s+"))
                    .map(Pattern::quote).collect(Collectors.joining("\\s*"))));
            case REGION -> List.of(Pattern.compile(Pattern.quote(value)));
            case PERSON -> value.length() >= 3
                    ? List.of(Pattern.compile(Pattern.quote(value)), Pattern.compile(Pattern.quote(value.substring(1)) + HONORIFIC))
                    : List.of(Pattern.compile(Pattern.quote(value)));
        };
    }

    /**
     * 주소를 낱말로 나눠 시, 구, 동 같은 꼬리를 뗀다("왕십리로"→"왕십리"). 시·도(첫 낱말)는 사람을 알아보는 힘이 약하고
     * 사칭 기관 이름("서울중앙지방검찰청")과 겹쳐 남긴다. 숫자가 든 낱말(번지, 동호수)은 낱말로 쓰면 다른 숫자까지 바꿔 뺀다.
     * 지점은 은행 이름을 빼고 "지점"을 뗀다("우리은행 왕십리지점"→"왕십리").
     */
    private static List<String> regionWords(CaseKnown known) {
        List<String> words = new ArrayList<>();
        if (known.victimAddress() != null) {
            String[] tokens = known.victimAddress().trim().split("[\\s,()]+");
            for (int i = 0; i < tokens.length; i++) {
                boolean province = i == 0 && tokens[i].matches(".+[시도]");
                if (!province && !tokens[i].matches(".*\\d.*")) {
                    words.add(stem(tokens[i], REGION_SUFFIX));
                }
            }
        }
        for (String branch : known.branches()) {
            Arrays.stream(branch.trim().split("\\s+"))
                    .filter(t -> !t.endsWith("은행"))
                    .forEach(t -> words.add(stem(t, BRANCH_SUFFIX)));
        }
        return words.stream().filter(w -> w.length() >= 2).toList();
    }

    private static String stem(String word, Pattern suffix) {
        String stem = suffix.matcher(word).replaceFirst("");
        return stem.length() >= 2 ? stem : word;
    }

    private void register(Kind kind, String prefix, String value) {
        if (value != null && !value.isBlank() && !byKey.containsKey(kind + key(kind, value))) {
            add(new MaskLabel(nextLabel(prefix), kind, value));
        }
    }

    private MaskLabel labelFor(Kind kind, String value) {
        MaskLabel found = byKey.get(kind + key(kind, value));
        if (found != null) {
            return found;
        }
        String prefix = switch (kind) {
            case PERSON -> "인물";
            case PHONE -> "전화";
            case EMAIL -> "메일";
            default -> throw new IllegalStateException("글에서 새로 찾지 않는 종류: " + kind);
        };
        MaskLabel label = new MaskLabel(nextLabel(prefix), kind, value);
        add(label);
        return label;
    }

    private void add(MaskLabel label) {
        labels.add(label);
        byKey.put(label.kind() + key(label.kind(), label.value()), label);
    }

    /** 피해자 쪽 이름표는 하나뿐인 경우가 대부분이라 첫 것에 번호를 붙이지 않는다([피해자계좌], [피해자계좌2]). */
    private String nextLabel(String prefix) {
        Pattern same = Pattern.compile("\\[" + prefix + "\\d*]");
        long n = labels.stream().filter(l -> same.matcher(l.label()).matches()).count() + 1;
        boolean victim = prefix.startsWith("피해자");
        return "[" + prefix + (victim && n == 1 ? "" : String.valueOf(n)) + "]";
    }

    private static String key(Kind kind, String value) {
        return switch (kind) {
            case ACCOUNT -> value.replaceAll("\\D", "");
            case PHONE -> {
                String digits = value.replaceAll("\\D", "");
                yield digits.startsWith("82") ? "0" + digits.substring(digits.startsWith("820") ? 3 : 2) : digits;
            }
            case EMAIL -> value.toLowerCase();
            case PERSON, ADDRESS, REGION -> value.replaceAll("\\s", "");
        };
    }
}
