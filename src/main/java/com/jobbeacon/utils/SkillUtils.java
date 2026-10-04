package com.jobbeacon.utils;

import java.text.Normalizer;
import java.util.Locale;

public class SkillUtils {

    private SkillUtils() {
    }

    public static String normalizeSkill(String value) {
        if (value == null) {
            return null;
        }

        return Normalizer
                .normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\s+", " ");
    }
}
