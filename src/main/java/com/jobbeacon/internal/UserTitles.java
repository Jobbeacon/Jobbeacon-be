package com.jobbeacon.internal;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.UserJobPreference;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The job titles a user is looking for. What they state in their job preference wins; the titles in their CV only stand
 * in for it while they have stated none. CV titles are stripped of the level ("Senior", "Nhân viên"), which postings
 * word differently for the same job.
 *
 * @param desired stated in the job preference
 * @param current what the CV says they do now; empty when {@code desired} is not
 * @param past    their latest earlier jobs in the CV; empty when {@code desired} is not
 */
record UserTitles(List<String> desired, List<String> current, List<String> past) {

    private static final int MAX_PAST_TITLES = 2;
    private static final Pattern LEVEL = Pattern.compile(
        "(?<![\\p{L}\\p{N}])(senior|sr\\.?|junior|jr\\.?|middle|mid-level|fresher|intern(ship)?|lead"
            + "|nh[aâ]n\\s+vi[eê]n|chuy[eê]n\\s+vi[eê]n|th[uự]c\\s+t[aậ]p\\s+sinh|cao\\s+c[aấ]p)(?![\\p{L}\\p{N}])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}+#.]+");

    static UserTitles of(UserJobPreference preference, CvProfile cv) {
        List<String> desired = preference == null || preference.getDesiredTitles() == null
            ? List.of()
            : preference.getDesiredTitles().stream().filter(title -> !words(title).isEmpty()).distinct().toList();
        if (!desired.isEmpty() || cv == null) {
            return new UserTitles(desired, List.of(), List.of());
        }

        List<CvParsedPayload.Experience> experiences = cv.getExperiences() == null
            ? List.of()
            : cv.getExperiences().stream().filter(Objects::nonNull).toList();
        List<String> current = withoutLevel(Stream.concat(
            Stream.ofNullable(cv.getCurrentTitle()),
            experiences.stream().filter(CvParsedPayload.Experience::current).map(CvParsedPayload.Experience::title)));
        Set<Set<String>> currentWords = current.stream().map(UserTitles::words).collect(Collectors.toSet());
        List<String> past = withoutLevel(experiences.stream()
                .filter(experience -> !experience.current())
                // "YYYY-MM" sorts as text; a job without dates counts as the oldest.
                .sorted(Comparator.comparing(UserTitles::lastMonth, Comparator.reverseOrder()))
                .map(CvParsedPayload.Experience::title))
            .stream()
            .filter(title -> !currentWords.contains(words(title)))
            .limit(MAX_PAST_TITLES)
            .toList();
        return new UserTitles(List.of(), current, past);
    }

    List<String> all() {
        return Stream.of(desired, current, past).flatMap(List::stream).toList();
    }

    boolean isEmpty() {
        return desired.isEmpty() && current.isEmpty() && past.isEmpty();
    }

    /** True when every word of one of the titles is in the job's title, ignoring case, accents and order. */
    static boolean anyMatches(List<String> titles, String jobTitle) {
        if (titles.isEmpty() || jobTitle == null) {
            return false;
        }
        Set<String> jobWords = words(jobTitle);
        return titles.stream().map(UserTitles::words).anyMatch(jobWords::containsAll);
    }

    private static String lastMonth(CvParsedPayload.Experience experience) {
        return Stream.of(experience.endDate(), experience.startDate()).filter(Objects::nonNull).findFirst().orElse("");
    }

    private static List<String> withoutLevel(Stream<String> titles) {
        Map<Set<String>, String> byWords = new LinkedHashMap<>();
        titles.filter(Objects::nonNull)
            .map(title -> LEVEL.matcher(Normalizer.normalize(title, Normalizer.Form.NFC)).replaceAll(" ")
                .replaceAll("\\s+", " ").trim())
            .forEach(title -> {
                Set<String> words = words(title);
                if (!words.isEmpty()) {
                    byWords.putIfAbsent(words, title);
                }
            });
        return List.copyOf(byWords.values());
    }

    private static Set<String> words(String text) {
        return Arrays.stream(SEPARATORS.split(plain(text)))
            .filter(word -> !word.isBlank())
            .collect(Collectors.toSet());
    }

    private static String plain(String text) {
        return ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
            .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
