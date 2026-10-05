package com.jobbeacon.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SkillUtils {

    private static final Pattern MISPLACED_TONE = Pattern.compile(
            "(?<!q)(o(?=[ae])|u(?=y))([aey])([\\u0300\\u0301\\u0303\\u0309\\u0323])(?![\\p{L}\\p{M}])");

    private SkillUtils() {
    }

    public static String normalizeSkill(String value) {
        if (value == null) {
            return null;
        }

        String normalized = Normalizer
                .normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        return normalizeToneMarks(normalized);
    }

    private static String normalizeToneMarks(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String moved = MISPLACED_TONE.matcher(decomposed).replaceAll("$1$3$2");
        return Normalizer.normalize(moved, Normalizer.Form.NFC);
    }
}
