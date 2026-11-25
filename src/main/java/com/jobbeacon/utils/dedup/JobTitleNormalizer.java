package com.jobbeacon.utils.dedup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class JobTitleNormalizer {

    public enum Seniority { INTERN, FRESHER, JUNIOR, MIDDLE, SENIOR, LEAD, MANAGER }

    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*]|\\([^)]*\\)|【[^】]*】");
    private static final String UNIT = "(?:tr|trieu|m|k|usd|vnd|dong)";
    private static final String NUM = "\\d[\\d ]*";
    private static final List<Pattern> NOISE = List.of(
            Pattern.compile("\\b(?:muc luong|luong|salary|thu nhap)\\b(?: (?:tu|len den|len toi|den|toi|upto|up to|khoang|tren|hon))*(?: " + NUM + ")(?: (?:den|toi) " + NUM + ")?(?: ?" + UNIT + "\\b)?"),
            Pattern.compile("\\b(?:upto|up to)(?: " + NUM + ")(?: ?" + UNIT + "\\b)?"),
            Pattern.compile("\\b" + NUM + "(?: ?(?:den|toi) ?" + NUM + ")? ?" + UNIT + "\\b"),
            Pattern.compile("\\b(?:tuyen gap|can gap|gap|hot|urgent|urgently)\\b"));
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final List<Rule> SENIORITY_RULES = List.of(
            new Rule(Seniority.MANAGER, "manager", "truong phong", "pho phong", "giam doc", "pho giam doc",
                    "director", "head of"),
            new Rule(Seniority.LEAD, "lead", "leader", "team lead", "tech lead", "truong nhom", "principal"),
            new Rule(Seniority.SENIOR, "senior", "sr"),
            new Rule(Seniority.MIDDLE, "middle", "mid", "mid level"),
            new Rule(Seniority.JUNIOR, "junior", "jr"),
            new Rule(Seniority.FRESHER, "fresher", "moi tot nghiep", "entry level"),
            new Rule(Seniority.INTERN, "intern", "internship", "thuc tap", "thuc tap sinh", "trainee"));

    private JobTitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String s = TextNormalizer.fold(BRACKETS.matcher(title).replaceAll(" "));
        for (Pattern p : NOISE) {
            s = p.matcher(s).replaceAll(" ");
        }
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    public static Optional<Seniority> seniority(String title) {
        String padded = " " + TextNormalizer.fold(title) + " ";
        for (Rule rule : SENIORITY_RULES) {
            for (String kw : rule.keywords()) {
                if (padded.contains(" " + kw + " ")) {
                    return Optional.of(rule.level());
                }
            }
        }
        return Optional.empty();
    }

    public static double tokenSimilarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() && tb.isEmpty()) {
            return 1.0;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        int union = ta.size() + tb.size() - inter.size();
        return (double) inter.size() / union;
    }

    private static Set<String> tokens(String s) {
        String folded = TextNormalizer.fold(s);
        return folded.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(folded.split(" ")));
    }

    private record Rule(Seniority level, String... keywords) {
    }
}
