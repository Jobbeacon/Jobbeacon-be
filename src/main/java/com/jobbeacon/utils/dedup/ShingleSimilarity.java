package com.jobbeacon.utils.dedup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Exact similarity over word shingle sets of texts processed by {@link #normalize}.
 */
public final class ShingleSimilarity {

    public static final int CONTAINMENT_SHINGLE = 3;

    private ShingleSimilarity() {
    }

    /**
     * Plain text without HTML, folded (same text as {@link MinHasher}).
     */
    public static String normalize(String text) {
        return ContentHasher.normalize(text);
    }

    /**
     * Distinct shingles; a text shorter than {@code size} words is a single shingle.
     */
    public static Set<String> shingles(String normalized, int size) {
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        String[] tokens = normalized.split(" ");
        int count = Math.max(1, tokens.length - size + 1);
        Set<String> result = new HashSet<>();
        for (int start = 0; start < count; start++) {
            int end = Math.min(tokens.length, start + size);
            result.add(String.join(" ", Arrays.copyOfRange(tokens, start, end)));
        }
        return result;
    }

    /**
     * |A∩B| / |A∪B|, 0 when either set is empty.
     */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int common = common(a, b);
        return (double) common / (a.size() + b.size() - common);
    }

    /**
     * |A∩B| / min(|A|,|B|), 0 when either set is empty.
     */
    public static double containment(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        return (double) common(a, b) / Math.min(a.size(), b.size());
    }

    private static int common(Set<String> a, Set<String> b) {
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = small == a ? b : a;
        return (int) small.stream().filter(large::contains).count();
    }
}
