package com.jobbeacon.semantic;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.utils.HtmlTextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The texts that are embedded to compare candidates with jobs. The parts that say most about the occupation come first,
 * so that cutting a long text to {@link #MAX_LENGTH} only loses detail.
 */
final class SemanticTexts {

    /** Well within what the embedding model reads; longer descriptions mostly add benefits and company blurbs. */
    static final int MAX_LENGTH = 4000;
    private static final int MAX_EXPERIENCES = 3;
    private static final int MAX_EXPERIENCE_LENGTH = 500;

    private SemanticTexts() {
    }

    static String job(String title, List<String> categories, List<String> skills, String description) {
        List<String> parts = new ArrayList<>();
        add(parts, "Job title", title);
        add(parts, "Category", String.join(", ", categories));
        add(parts, "Skills", String.join(", ", skills));
        add(parts, "Description", description == null ? null : HtmlTextUtils.toPlainText(description));
        return cut(String.join("\n", parts));
    }

    /** Empty when there is nothing to go by. */
    static String candidate(UserJobPreference preference, CvProfile cv, List<String> skills) {
        List<String> parts = new ArrayList<>();
        if (preference != null && preference.getDesiredTitles() != null) {
            add(parts, "Desired position", preference.getDesiredTitles().stream()
                .filter(title -> title != null && !title.isBlank()).map(String::strip).collect(Collectors.joining("; ")));
        }
        if (cv != null) {
            add(parts, "Current position", cv.getCurrentTitle());
            add(parts, "Summary", cv.getSummary());
            List<String> experiences = latest(cv.getExperiences()).stream()
                .map(SemanticTexts::experience)
                .filter(text -> !text.isBlank())
                .toList();
            if (!experiences.isEmpty()) {
                parts.add("Experience:\n" + String.join("\n", experiences));
            }
        }
        add(parts, "Skills", String.join(", ", skills));
        return cut(String.join("\n", parts));
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Current jobs first, then by when they ended; a job without dates counts as the oldest. */
    private static List<CvParsedPayload.Experience> latest(List<CvParsedPayload.Experience> experiences) {
        if (experiences == null) {
            return List.of();
        }
        return experiences.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(CvParsedPayload.Experience::current, Comparator.reverseOrder())
                // "YYYY-MM" sorts as text.
                .thenComparing(SemanticTexts::lastMonth, Comparator.reverseOrder()))
            .limit(MAX_EXPERIENCES)
            .toList();
    }

    private static String lastMonth(CvParsedPayload.Experience experience) {
        return Stream.of(experience.endDate(), experience.startDate()).filter(Objects::nonNull).findFirst().orElse("");
    }

    private static String experience(CvParsedPayload.Experience experience) {
        String text = Stream.of(experience.title(), experience.description())
            .filter(part -> part != null && !part.isBlank())
            .map(String::strip)
            .collect(Collectors.joining(": "));
        if (experience.skills() != null && !experience.skills().isEmpty()) {
            text += " (" + String.join(", ", experience.skills().stream().filter(Objects::nonNull).toList()) + ")";
        }
        text = text.strip();
        return text.isEmpty() ? "" : "- " + cut(text, MAX_EXPERIENCE_LENGTH);
    }

    private static void add(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + ": " + value.strip().replaceAll("\\s+", " "));
        }
    }

    private static String cut(String text) {
        return cut(text, MAX_LENGTH);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
