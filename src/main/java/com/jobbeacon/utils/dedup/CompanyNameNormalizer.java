package com.jobbeacon.utils.dedup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CompanyNameNormalizer {

    private static final List<String[]> PREFIXES = phrases(
            "cong ty trach nhiem huu han", "cong ty co phan", "cong ty tnhh", "cong ty cp", "cong ty",
            "trach nhiem huu han", "tnhh", "co phan", "cp", "mtv", "mot thanh vien", "hai thanh vien", "2tv");

    private static final List<String[]> SUFFIXES = phrases(
            "trach nhiem huu han", "mot thanh vien", "hai thanh vien", "co phan", "viet nam", "vietnam",
            "tnhh", "mtv", "2tv", "cp", "jsc", "co", "ltd", "limited", "company", "corp", "corporation",
            "inc", "llc", "vn", "pte");

    private static final List<String[][]> ABBREVIATIONS = List.of(
            abbreviation("thuong mai co phan", "tmcp"),
            abbreviation("trach nhiem huu han", "tnhh"),
            abbreviation("mot thanh vien", "mtv"),
            abbreviation("hai thanh vien", "2tv"),
            abbreviation("co phan", "cp"));

    private CompanyNameNormalizer() {
    }

    public static String normalize(String name) {
        String folded = TextNormalizer.fold(name);
        if (folded.isEmpty()) {
            return folded;
        }
        List<String> tokens = abbreviate(new ArrayList<>(Arrays.asList(folded.split(" "))));
        boolean changed = true;
        while (changed && !tokens.isEmpty()) {
            changed = stripPrefix(tokens) | stripSuffix(tokens);
        }
        return tokens.isEmpty() ? folded : String.join(" ", tokens);
    }

    private static List<String> abbreviate(List<String> tokens) {
        for (String[][] a : ABBREVIATIONS) {
            List<String> phrase = Arrays.asList(a[0]);
            List<String> out = new ArrayList<>(tokens.size());
            int i = 0;
            while (i < tokens.size()) {
                if (i + phrase.size() <= tokens.size() && tokens.subList(i, i + phrase.size()).equals(phrase)) {
                    out.add(a[1][0]);
                    i += phrase.size();
                } else {
                    out.add(tokens.get(i++));
                }
            }
            tokens = out;
        }
        return tokens;
    }

    private static String[][] abbreviation(String phrase, String abbr) {
        return new String[][]{phrase.split(" "), {abbr}};
    }

    private static boolean stripPrefix(List<String> tokens) {
        for (String[] p : PREFIXES) {
            if (p.length <= tokens.size() && tokens.subList(0, p.length).equals(Arrays.asList(p))) {
                tokens.subList(0, p.length).clear();
                return true;
            }
        }
        return false;
    }

    private static boolean stripSuffix(List<String> tokens) {
        for (String[] p : SUFFIXES) {
            int n = tokens.size();
            if (p.length <= n && tokens.subList(n - p.length, n).equals(Arrays.asList(p))) {
                tokens.subList(n - p.length, n).clear();
                return true;
            }
        }
        return false;
    }

    private static List<String[]> phrases(String... values) {
        return Arrays.stream(values).map(v -> v.split(" ")).toList();
    }
}
