package com.jobbeacon.utils.dedup;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TextNormalizer() {
    }

    public static String fold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");
        s = s.replace('đ', 'd').replace('Đ', 'd').toLowerCase(java.util.Locale.ROOT);
        return NON_ALNUM.matcher(s).replaceAll(" ").trim();
    }
}
