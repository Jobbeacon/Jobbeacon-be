package com.jobbeacon.utils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds a comparison key for company names so that the copies a crawler creates for one company
 * ("CÔNG TY TNHH ABC Việt Nam", "ABC Vietnam Co., Ltd") collapse to the same key ("abc").
 */
public final class CompanyNameUtils {

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

  /** Legal-form and country words, longest first so multi-word phrases are removed before their parts. */
  private static final List<String> NOISE = List.of(
      "trach nhiem huu han mot thanh vien",
      "trach nhiem huu han",
      "mot thanh vien",
      "co phan",
      "cong ty",
      "tap doan",
      "chi nhanh",
      "viet nam",
      "vietnam",
      "company limited",
      "corporation",
      "company",
      "limited",
      "joint stock",
      "tnhh",
      "mtv",
      "jsc",
      "corp",
      "ltd",
      "llc",
      "inc",
      "co",
      "cp",
      "vn"
  );

  private CompanyNameUtils() {
  }

  public static String fold(String value) {
    if (value == null) {
      return "";
    }
    String text = value.replace('đ', 'd').replace('Đ', 'D');
    text = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    text = NON_ALNUM.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
    return text.trim().replaceAll("\\s+", " ");
  }

  public static String key(String name) {
    String folded = fold(name);
    String padded = " " + folded + " ";
    for (String noise : NOISE) {
      padded = padded.replace(" " + noise + " ", " ");
      // a phrase can occur twice in a row ("co co"); repeat once more for the shifted match
      padded = padded.replace(" " + noise + " ", " ");
    }
    String key = padded.trim().replaceAll("\\s+", " ");
    return key.isEmpty() ? folded : key;
  }

  /**
   * True when two names likely denote the same company: same key, or one key is the other plus
   * extra words and the shared part is distinctive enough (at least 4 characters).
   */
  public static boolean likelySame(String keyA, String keyB) {
    if (keyA == null || keyB == null || keyA.isEmpty() || keyB.isEmpty()) {
      return false;
    }
    if (keyA.equals(keyB)) {
      return true;
    }
    String shorter = keyA.length() <= keyB.length() ? keyA : keyB;
    String longer = shorter == keyA ? keyB : keyA;
    if (shorter.length() < 4) {
      return false;
    }
    return (" " + longer + " ").contains(" " + shorter + " ");
  }
}
