package com.jobbeacon.cv;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.dto.cv.CvParsedPayload.Certification;
import com.jobbeacon.dto.cv.CvParsedPayload.Contact;
import com.jobbeacon.dto.cv.CvParsedPayload.Education;
import com.jobbeacon.dto.cv.CvParsedPayload.Experience;
import com.jobbeacon.dto.cv.CvParsedPayload.SkillItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combines the deterministic and model extraction results into the final payload (§7.4):
 * cross-checks untrusted model values against the raw text, merges both sources, normalizes
 * dates to {@code YYYY-MM} and computes experience months.
 *
 * <p>Skills are NOT resolved against the {@code skill} table here; {@code skillId/skillName}
 * are carried over from the inputs and the caller resolves them via {@code SkillLookupService}.
 *
 * <p>{@code lowConfidenceFields} uses snake_case paths with 0-based indexes into the final
 * payload lists, matching the API JSON: {@code contact.email}, {@code contact.phone},
 * {@code contact.links}, {@code experiences[1].start_date}, {@code educations[0].end_date},
 * {@code certifications[2].date}.
 */
@Slf4j
@Component
public class CvPostProcessor {

  private static final ZoneId CV_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final int EVIDENCE_MAX_LENGTH = 200;
  private static final Pattern PHONE_CANDIDATE = Pattern.compile("\\+?\\d[\\d\\s().\\-]{6,}\\d");
  private static final Pattern TRUNK_ZERO = Pattern.compile("\\(\\s*0\\s*\\)");
  // characters that may continue an email/URL token; a trailing "." is allowed only as sentence punctuation
  private static final String EMAIL_BEFORE = "(?<![a-z0-9._%+\\-])";
  private static final String URL_BEFORE = "(?<![a-z0-9._~%+\\-/@:])";
  private static final String TOKEN_AFTER = "(?![a-z0-9_~%+\\-]|\\.[a-z0-9])";
  private static final Pattern URL_SCHEME = Pattern.compile("^(https?://)?(www\\.)?");

  private final Clock clock;

  @Autowired
  public CvPostProcessor() {
    this(Clock.system(CV_ZONE));
  }

  CvPostProcessor(Clock clock) {
    this.clock = clock;
  }

  /**
   * @param model         model output (untrusted), or null when the model was not called
   * @param deterministic deterministic (regex/gazetteer) partial result, or null
   * @param rawText       CV text the extraction was run on, used for cross-checking
   */
  public CvPostProcessResult process(CvParsedPayload model, CvParsedPayload deterministic, String rawText) {
    RawText raw = new RawText(rawText);
    CvParsedPayload m = model == null ? CvParsedPayload.empty() : model;
    CvParsedPayload d = deterministic == null ? CvParsedPayload.empty() : deterministic;
    List<String> dropped = new ArrayList<>();
    Set<String> lowConfidence = new LinkedHashSet<>();
    addAll(lowConfidence, d.lowConfidenceFields());
    addAll(lowConfidence, m.lowConfidenceFields());

    Contact contact = mergeContact(m.contact(), d.contact(), raw, dropped, lowConfidence);

    YearMonth today = YearMonth.now(clock);
    List<Experience> experiences = new ArrayList<>();
    List<Interval> intervals = new ArrayList<>();
    List<Experience> sourceExperiences = firstNonEmpty(m.experiences(), d.experiences());
    for (int i = 0; i < sourceExperiences.size(); i++) {
      Experience e = sourceExperiences.get(i);
      if (e == null) {
        continue;
      }
      int index = experiences.size();
      NormalizedExperience ne = normalizeExperience(e, index, today, lowConfidence);
      experiences.add(ne.experience());
      intervals.add(ne.interval());
    }

    int hallucinated = 0;
    Map<String, SkillAcc> skills = new LinkedHashMap<>();
    for (SkillItem item : nullSafe(d.skills())) {
      accumulate(skills, item);
    }
    for (int i = 0; i < nullSafe(m.skills()).size(); i++) {
      SkillItem item = m.skills().get(i);
      String name = skillName(item);
      if (name == null) {
        continue;
      }
      if (!raw.containsToken(name)) {
        hallucinated++;
        dropped.add("skills[" + i + "]");
        continue;
      }
      accumulate(skills, item);
    }

    List<SkillItem> finalSkills = new ArrayList<>();
    for (SkillAcc acc : skills.values()) {
      finalSkills.add(finalizeSkill(acc, experiences, intervals, raw));
    }

    List<Interval> valid = intervals.stream().filter(Objects::nonNull).toList();
    Integer totalMonths = valid.isEmpty()
        ? (experiences.isEmpty() ? Integer.valueOf(0) : null)
        : Integer.valueOf(mergedMonths(valid));

    CvParsedPayload payload = new CvParsedPayload(
        contact,
        firstNonBlank(m.currentTitle(), d.currentTitle()),
        firstNonBlank(m.summary(), d.summary()),
        experiences,
        normalizeEducations(firstNonEmpty(m.educations(), d.educations()), lowConfidence),
        List.copyOf(firstNonEmpty(m.projects(), d.projects())),
        normalizeCertifications(firstNonEmpty(m.certifications(), d.certifications()), lowConfidence),
        List.copyOf(firstNonEmpty(m.languages(), d.languages())),
        finalSkills,
        List.copyOf(lowConfidence),
        totalMonths);
    log.info("(process) post-processed cv: skills={}, hallucinatedSkills={}, droppedFields={}, lowConfidenceFields={}",
        finalSkills.size(), hallucinated, dropped.size(), lowConfidence.size());
    return new CvPostProcessResult(payload, hallucinated, List.copyOf(dropped));
  }

  // ---------------------------------------------------------------- contact

  private Contact mergeContact(Contact mc, Contact dc, RawText raw, List<String> dropped, Set<String> lowConfidence) {
    String detEmail = dc == null ? null : blankToNull(dc.email());
    String detPhone = dc == null ? null : blankToNull(dc.phone());

    String modelEmail = mc == null ? null : blankToNull(mc.email());
    if (modelEmail != null && !raw.containsEmail(modelEmail)) {
      dropped.add("contact.email");
      if (detEmail == null) {
        lowConfidence.add("contact.email");
      }
      modelEmail = null;
    }
    String modelPhone = mc == null ? null : blankToNull(mc.phone());
    if (modelPhone != null && !raw.containsPhone(modelPhone)) {
      dropped.add("contact.phone");
      if (detPhone == null) {
        lowConfidence.add("contact.phone");
      }
      modelPhone = null;
    }

    Map<String, String> links = new LinkedHashMap<>();
    for (String link : nullSafe(dc == null ? null : dc.links())) {
      if (blankToNull(link) != null) {
        links.putIfAbsent(normalizeUrl(link), link.trim());
      }
    }
    List<String> modelLinks = nullSafe(mc == null ? null : mc.links());
    for (int i = 0; i < modelLinks.size(); i++) {
      String link = blankToNull(modelLinks.get(i));
      if (link == null) {
        continue;
      }
      if (!raw.containsUrl(link)) {
        dropped.add("contact.links[" + i + "]");
        lowConfidence.add("contact.links");
        continue;
      }
      links.putIfAbsent(normalizeUrl(link), link);
    }

    String email = detEmail != null ? detEmail : modelEmail;
    String phone = detPhone != null ? detPhone : modelPhone;
    String fullName = firstNonBlank(mc == null ? null : mc.fullName(), dc == null ? null : dc.fullName());
    String location = firstNonBlank(mc == null ? null : mc.location(), dc == null ? null : dc.location());
    return new Contact(fullName, email, phone, List.copyOf(links.values()), location);
  }

  // ---------------------------------------------------------------- dates

  private NormalizedExperience normalizeExperience(Experience e, int index, YearMonth today, Set<String> lowConfidence) {
    String prefix = "experiences[" + index + "].";
    YearMonth start = normalizeDate(e.startDate(), false, prefix + "start_date", lowConfidence);
    boolean endIsPresent = DateRangeNormalizer.isPresent(e.endDate());
    YearMonth end = endIsPresent ? null : normalizeDate(e.endDate(), true, prefix + "end_date", lowConfidence);
    boolean current = endIsPresent || (end == null && e.current());

    Interval interval = null;
    if (start == null) {
      lowConfidence.add(prefix + "start_date");
    } else {
      YearMonth effectiveEnd = current ? today : end;
      if (effectiveEnd == null) {
        lowConfidence.add(prefix + "end_date");
      } else if (effectiveEnd.isBefore(start)) {
        lowConfidence.add(current ? prefix + "start_date" : prefix + "end_date");
      } else {
        interval = new Interval(start, effectiveEnd);
      }
    }
    Experience normalized = new Experience(e.title(), e.company(), DateRangeNormalizer.format(start),
        DateRangeNormalizer.format(end), current, e.description(), e.skills());
    return new NormalizedExperience(normalized, interval);
  }

  private List<Education> normalizeEducations(List<Education> source, Set<String> lowConfidence) {
    List<Education> result = new ArrayList<>();
    for (Education e : source) {
      if (e == null) {
        continue;
      }
      String prefix = "educations[" + result.size() + "].";
      YearMonth start = normalizeDate(e.startDate(), false, prefix + "start_date", lowConfidence);
      YearMonth end = DateRangeNormalizer.isPresent(e.endDate())
          ? null : normalizeDate(e.endDate(), true, prefix + "end_date", lowConfidence);
      result.add(new Education(e.school(), e.degree(), e.major(),
          DateRangeNormalizer.format(start), DateRangeNormalizer.format(end)));
    }
    return result;
  }

  private List<Certification> normalizeCertifications(List<Certification> source, Set<String> lowConfidence) {
    List<Certification> result = new ArrayList<>();
    for (Certification c : source) {
      if (c == null) {
        continue;
      }
      String path = "certifications[" + result.size() + "].date";
      YearMonth date = normalizeDate(c.date(), true, path, lowConfidence);
      result.add(new Certification(c.name(), c.issuer(), DateRangeNormalizer.format(date)));
    }
    return result;
  }

  /** Blank → null silently; non-blank but unparseable (incl. a "present" word where not allowed) → null + flag. */
  private YearMonth normalizeDate(String value, boolean endOfRange, String path, Set<String> lowConfidence) {
    if (value == null || value.isBlank()) {
      return null;
    }
    Optional<YearMonth> parsed = DateRangeNormalizer.parse(value, endOfRange);
    if (parsed.isEmpty()) {
      lowConfidence.add(path);
    }
    return parsed.orElse(null);
  }

  static int mergedMonths(List<Interval> intervals) {
    List<Interval> sorted = new ArrayList<>(intervals);
    sorted.sort(Comparator.comparing(Interval::start));
    int total = 0;
    YearMonth curStart = null;
    YearMonth curEnd = null;
    for (Interval interval : sorted) {
      if (curStart == null) {
        curStart = interval.start();
        curEnd = interval.end();
      } else if (!interval.start().isAfter(curEnd.plusMonths(1))) {
        if (interval.end().isAfter(curEnd)) {
          curEnd = interval.end();
        }
      } else {
        total += months(curStart, curEnd);
        curStart = interval.start();
        curEnd = interval.end();
      }
    }
    if (curStart != null) {
      total += months(curStart, curEnd);
    }
    return total;
  }

  /** Inclusive month count: 2020-01..2020-12 = 12. */
  private static int months(YearMonth start, YearMonth end) {
    return (int) ChronoUnit.MONTHS.between(start, end) + 1;
  }

  // ---------------------------------------------------------------- skills

  private void accumulate(Map<String, SkillAcc> skills, SkillItem item) {
    String name = skillName(item);
    if (name == null) {
      return;
    }
    String key = RawText.clean(name);
    SkillAcc acc = skills.computeIfAbsent(key, k -> new SkillAcc(name));
    if (acc.skillId == null && item.skillId() != null) {
      acc.skillId = item.skillId();
      acc.skillName = item.skillName();
    }
    if (item.confidence() != null && (acc.confidence == null || item.confidence() > acc.confidence)) {
      acc.confidence = item.confidence();
    }
    if (acc.evidence == null) {
      acc.evidence = blankToNull(item.evidence());
    }
  }

  private SkillItem finalizeSkill(SkillAcc acc, List<Experience> experiences, List<Interval> intervals, RawText raw) {
    Pattern token = RawText.tokenPattern(acc.raw);
    List<Interval> used = new ArrayList<>();
    for (int i = 0; i < experiences.size(); i++) {
      Interval interval = intervals.get(i);
      if (interval != null && mentions(experiences.get(i), acc.raw, token)) {
        used.add(interval);
      }
    }
    // experienceMonths/lastUsedAt from the inputs are untrusted: only values derived from experiences are kept
    Integer experienceMonths = null;
    String lastUsedAt = null;
    if (!used.isEmpty()) {
      experienceMonths = mergedMonths(used);
      lastUsedAt = DateRangeNormalizer.format(
          used.stream().map(Interval::end).max(Comparator.naturalOrder()).orElseThrow());
    }
    String evidence = acc.evidence != null ? acc.evidence : raw.evidenceLine(token);
    return new SkillItem(acc.raw, acc.skillId, acc.skillName, acc.confidence, evidence, experienceMonths, lastUsedAt);
  }

  private static boolean mentions(Experience e, String skill, Pattern token) {
    for (String s : nullSafe(e.skills())) {
      if (s != null && RawText.clean(s).equals(RawText.clean(skill))) {
        return true;
      }
    }
    return e.description() != null && token.matcher(RawText.clean(e.description())).find();
  }

  private static String skillName(SkillItem item) {
    if (item == null) {
      return null;
    }
    String name = blankToNull(item.raw());
    return name != null ? name : blankToNull(item.skillName());
  }

  // ---------------------------------------------------------------- utils

  private static String normalizeUrl(String url) {
    String s = url.trim().toLowerCase(Locale.ROOT);
    s = URL_SCHEME.matcher(s).replaceFirst("");
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  /** Digits only; "+84", "0084", "+84 (0)" and "84 0" all become the domestic leading "0". */
  static String canonicalPhone(String phone) {
    String digits = TRUNK_ZERO.matcher(phone).replaceAll("").replaceAll("\\D", "");
    if (digits.startsWith("00")) {
      digits = digits.substring(2);
    }
    if (digits.startsWith("84") && digits.length() >= 11) {
      String national = digits.substring(2);
      if (national.startsWith("0")) {
        national = national.substring(1);
      }
      digits = "0" + national;
    }
    return digits;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String firstNonBlank(String a, String b) {
    String first = blankToNull(a);
    return first != null ? first : blankToNull(b);
  }

  private static <T> List<T> firstNonEmpty(List<T> a, List<T> b) {
    if (a != null && !a.isEmpty()) {
      return a;
    }
    return b == null ? List.of() : b;
  }

  private static <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static void addAll(Set<String> target, List<String> values) {
    for (String v : nullSafe(values)) {
      if (blankToNull(v) != null) {
        target.add(v.trim());
      }
    }
  }

  record Interval(YearMonth start, YearMonth end) {
  }

  private record NormalizedExperience(Experience experience, Interval interval) {
  }

  private static final class SkillAcc {
    private final String raw;
    private UUID skillId;
    private String skillName;
    private Double confidence;
    private String evidence;

    private SkillAcc(String raw) {
      this.raw = raw;
    }
  }

  /** Raw CV text pre-normalized once for the cross-checks. */
  private static final class RawText {
    private final String original;
    private final String lower;
    private final String urlForm;
    private final Set<String> phones = new HashSet<>();

    private RawText(String text) {
      this.original = text == null ? "" : text;
      this.lower = clean(original);
      this.urlForm = lower.replace("https://", "").replace("http://", "").replace("www.", "");
      Matcher matcher = PHONE_CANDIDATE.matcher(original);
      while (matcher.find()) {
        phones.add(canonicalPhone(matcher.group()));
      }
    }

    static String clean(String s) {
      return Normalizer.normalize(s, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Whole-token match: "Java" does not match inside "JavaScript", "C" not inside "C++"/"C#",
     * "Node" not inside "Node.js", "NET" not inside ".NET" (a "." followed by a letter/digit
     * continues the token; a trailing sentence "." does not).
     */
    static Pattern tokenPattern(String skill) {
      return Pattern.compile("(?<![\\p{L}\\p{N}.])" + Pattern.quote(clean(skill))
          + "(?![\\p{L}\\p{N}+#]|\\.[\\p{L}\\p{N}])");
    }

    boolean containsToken(String skill) {
      return tokenPattern(skill).matcher(lower).find();
    }

    boolean containsEmail(String email) {
      return email.contains("@")
          && Pattern.compile(EMAIL_BEFORE + Pattern.quote(clean(email)) + TOKEN_AFTER).matcher(lower).find();
    }

    boolean containsPhone(String phone) {
      String canonical = canonicalPhone(phone);
      return canonical.length() >= 8 && phones.contains(canonical);
    }

    boolean containsUrl(String url) {
      String normalized = normalizeUrl(url);
      return !normalized.isEmpty()
          && Pattern.compile(URL_BEFORE + Pattern.quote(normalized) + TOKEN_AFTER).matcher(urlForm).find();
    }

    String evidenceLine(Pattern token) {
      for (String line : original.split("\\R")) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && token.matcher(clean(trimmed)).find()) {
          return trimmed.length() > EVIDENCE_MAX_LENGTH ? trimmed.substring(0, EVIDENCE_MAX_LENGTH) : trimmed;
        }
      }
      return null;
    }
  }
}
