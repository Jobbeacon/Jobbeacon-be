package com.jobbeacon.cv;

import com.jobbeacon.cv.DeterministicResult.ContactInfo;
import com.jobbeacon.cv.DeterministicResult.DateRange;
import com.jobbeacon.cv.DeterministicResult.ExperienceBlock;
import com.jobbeacon.cv.DeterministicResult.Section;
import com.jobbeacon.cv.DeterministicResult.SkillMatch;
import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.dto.skill.SkillRef;
import com.jobbeacon.service.SkillLookupService;
import com.jobbeacon.utils.SkillUtils;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deterministic pass of the CV pipeline (analysis doc §7.1–7.2): preprocessing, section detection
 * (vi + en), regex for contact / date ranges, experience blocks, skill gazetteer and quality metrics.
 * Pure apart from {@link SkillLookupService#nameIndex()}. Never logs CV content.
 *
 * <p>Layout complexity (L) is a simple flag, true when any of:
 * <ul>
 *   <li>more than 30% of non-empty lines contain an internal gap of 3+ spaces / a tab (columns),
 *       with at least {@value #MIN_LINES_FOR_RATIO} lines;</li>
 *   <li>more than 25% of non-empty lines are fragments of at most 3 chars (interleaved text),
 *       with at least {@value #MIN_LINES_FOR_RATIO} lines;</li>
 *   <li>a line consists of two section headings without connector ("KINH NGHIỆM KỸ NĂNG"),
 *       i.e. two columns merged on one line;</li>
 *   <li>at least two Vietnamese and two English headings (bilingual CV).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeterministicExtractor {

  static final int MIN_LINES_FOR_RATIO = 10;
  private static final double GAP_LINE_RATIO = 0.30;
  private static final double SHORT_LINE_RATIO = 0.25;
  private static final int MAX_HEADING_LENGTH = 60;
  private static final int MAX_HEADING_WORDS = 6;
  private static final int MAX_HEADER_LINE_LENGTH = 80;
  private static final int MAX_CANDIDATE_LENGTH = 100;
  private static final int MAX_HEADER_WORDS_ENDING_WITH_DOT = 6;
  private static final int EDGE_LINES = 2;
  private static final int MAX_EVIDENCE_LENGTH = 300;
  private static final int NAME_SCAN_LINES = 3;
  static final int SHORT_SKILL_LENGTH = 2;
  private static final int MAX_HEADER_LINES = 2;
  private static final int MAX_ANCHOR_BULLET_WORDS = 8;
  private static final int MAX_ANCHOR_DATE_OFFSET = 3;

  private static final Set<Section> EXPECTED_SECTIONS =
      EnumSet.of(Section.EXPERIENCE, Section.EDUCATION, Section.SKILLS, Section.CONTACT);

  private static final Map<String, Heading> HEADINGS = buildHeadings();

  // ---- regex on original text ----
  private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
  private static final Pattern URL = Pattern.compile(
      "(?i)(?:https?://|www\\.)[^\\s|,;<>()\\[\\]\"]+"
          + "|(?<![\\w@.])(?:[a-z0-9\\-]+\\.)?(?:linkedin\\.com|github\\.com|gitlab\\.com|bitbucket\\.org"
          + "|behance\\.net|dribbble\\.com|facebook\\.com|fb\\.com|twitter\\.com|medium\\.com"
          + "|stackoverflow\\.com|topcv\\.vn)(?:/[^\\s|,;<>()\\[\\]\"]*)?");
  private static final Pattern PHONE_VN = Pattern.compile(
      "(?<![\\d+])(?:\\(\\+84\\)|\\+84|\\(0\\d{1,3}\\)|0)(?:[ .\\-]?\\d){6,10}(?!\\d)");
  private static final Pattern PHONE_INTL = Pattern.compile(
      "(?<![\\d+])\\+(?!84)\\d{1,3}(?:[ .\\-]?\\(?\\d{1,4}\\)?){2,6}(?!\\d)");
  private static final Pattern TOKEN = Pattern.compile(
      "(?:(?<![^\\s(\\[{,;/|:])\\.)?[\\p{L}\\p{N}](?:[\\p{L}\\p{N}+#._\\-]*[\\p{L}\\p{N}+#])?");
  private static final Pattern CHUNK_SPLIT = Pattern.compile("[\\s,;|()\\[\\]{}]+");
  private static final Pattern CANDIDATE_SPLIT =
      Pattern.compile("(?iu)\\s*[|•·]\\s*|\\s+[-–—]\\s+|\\s+(?:at|tại)\\s+|\\s*@\\s*");
  private static final Pattern BULLET = Pattern.compile("^(?:[-•●○◦▪▫■□►▶➢➤✓✔·*+]|\\d{1,2}[.)]\\s)");
  private static final Pattern HEADING_NUMBERING = Pattern.compile("^(?:\\d{1,2}|[IVXivx]{1,4})[.)]\\s+");
  private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
  private static final Pattern INTERNAL_GAP = Pattern.compile("\\S(?: {3,}|\\t)\\S");
  private static final Pattern FULL_NAME = Pattern.compile("[\\p{L}.'\\-]+(?: [\\p{L}.'\\-]+)+");
  private static final Pattern SHORT_NUMBER = Pattern.compile("(?<!\\d)\\d{1,3}(?!\\d)");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]");
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n]]");

  // ---- regex on folded text (lowercase, diacritics removed, same length as original) ----
  private static final String YEAR = "(?:19|20)\\d{2}";
  private static final String MONTH_NAME = "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?"
      + "|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";
  private static final String DATE = "(?:(?:thang|th\\.?|t)\\s*\\d{1,2}\\s*(?:[/.\\-]|\\s+nam|\\s)\\s*" + YEAR
      + "|" + MONTH_NAME + "\\.?\\s*,?\\s*" + YEAR
      + "|\\d{1,2}\\s*[/.\\-]\\s*" + YEAR
      + "|" + YEAR + ")";
  private static final String PRESENT =
      "(?:den\\s+nay|hien\\s*tai|hien\\s*nay|nay|present|now|currently|current|today)";
  private static final Pattern DATE_RANGE = Pattern.compile(
      "(?<![\\p{L}\\d])(" + DATE + ")\\s*(?:-|–|—|~|to|until|den|toi)\\s*(" + DATE + "|" + PRESENT + ")"
          + "(?![\\p{L}\\d])");
  private static final Pattern PRESENT_ONLY = Pattern.compile("^" + PRESENT + "$");
  private static final Pattern DATE_THANG = Pattern.compile(
      "^(?:thang|th\\.?|t)\\s*(\\d{1,2})\\s*(?:[/.\\-]|\\s+nam|\\s)\\s*(" + YEAR + ")$");
  private static final Pattern DATE_NAME = Pattern.compile("^(" + MONTH_NAME + ")\\.?\\s*,?\\s*(" + YEAR + ")$");
  private static final Pattern DATE_NUM = Pattern.compile("^(\\d{1,2})\\s*[/.\\-]\\s*(" + YEAR + ")$");
  private static final Pattern DATE_YEAR = Pattern.compile("^(" + YEAR + ")$");
  private static final Pattern PAGE_NUMBER = Pattern.compile(
      "^(?:(?:page|trang|p\\.?)\\s*)?[-–\\s]*\\d{1,3}[-–\\s]*(?:(?:/|of|tren)\\s*\\d{1,3})?$");
  private static final Pattern TITLE_KEYWORD = Pattern.compile("\\b(?:developer|engineer|intern|internship"
      + "|thuc tap|thuc tap sinh|manager|lead|leader|ky su|lap trinh vien|chuyen vien|nhan vien|analyst"
      + "|designer|tester|qa|qc|architect|consultant|specialist|truong nhom|truong phong|giam doc|director"
      + "|officer|administrator|admin|devops|scientist|executive|assistant|tro ly|coordinator|head|cto|ceo"
      + "|programmer|fresher|junior|senior|staff|ke toan|accountant)\\b");
  private static final Pattern COMPANY_KEYWORD = Pattern.compile("\\b(?:cong ty|company|corp|corporation"
      + "|jsc|ltd|llc|inc|tap doan|group|bank|ngan hang|technology|technologies|software|solutions?|studio"
      + "|agency|tnhh|co phan|labs?|systems?)\\b");
  private static final Pattern SCHOOL_KEYWORD = Pattern.compile("\\b(?:university|universite|college|institute"
      + "|academy|polytechnic|school|dai hoc|cao dang|hoc vien|trung cap|thpt|ptth"
      + "|truong (?:dai hoc|cao dang|trung hoc|thpt|pho thong|chuyen))\\b");
  private static final Pattern DEGREE = Pattern.compile("(?<![\\p{L}.])(?:bachelor(?:'s)?(?:\\s+degree)?"
      + "|master(?:'s)?(?:\\s+degree)?|doctor(?:ate)?|diploma|associate(?:'s)?\\s+degree"
      + "|b\\.?\\s?sc\\.?|b\\.?\\s?eng\\.?|b\\.a\\.|m\\.?\\s?sc\\.?|m\\.?\\s?eng\\.?|mba|ph\\.?\\s?d\\.?"
      + "|bang cu nhan|bang ky su|cu nhan|ky su|thac si|tien si)"
      + "(?:\\s+of\\s+(?:science|arts|engineering|technology|business administration|laws?|education))?"
      + "(?![\\p{L}])");
  private static final Pattern MAJOR_PREFIX = Pattern.compile(
      "^(?:[\\s,:;\\-–—]|in\\b|of\\b|chuyen nganh\\b|nganh\\b|major(?:\\s+in)?\\b|specialization\\b"
          + "|field of study\\b)+");
  private static final Pattern MAJOR_LABEL = Pattern.compile(
      "^(?:chuyen nganh|nganh|major|specialization|field of study)\\b");
  private static final Pattern GRADE = Pattern.compile("\\b(?:gpa|cpa|diem|grade|xep loai|loai|honou?rs?)\\b");
  private static final Pattern YEAR_TOKEN = Pattern.compile("(?<!\\d)" + YEAR + "(?!\\d)");
  private static final Pattern HEADING_CONNECTOR = Pattern.compile("[&/,+]|\\b(?:and|va)\\b");
  private static final String NON_GARBAGE_SYMBOLS = "•●○◦▪▫■□►▶➢➤✓✔★☆©®°±·«»|~^`";
  private static final String NON_GARBAGE_LATIN1 = "©®°±·«»";

  private final SkillLookupService skillLookupService;

  /** Extracts from native PDF text, using per-page counts to split pages for header/footer removal. */
  public DeterministicResult extract(PdfText pdfText) {
    return extract(splitPages(pdfText));
  }

  /** Extracts from plain text; form feeds ({@code \f}) are treated as page breaks. */
  public DeterministicResult extract(String rawText) {
    return extract(List.of((rawText == null ? "" : rawText).split("\f", -1)));
  }

  private DeterministicResult extract(List<String> rawPages) {
    long start = System.nanoTime();
    List<List<String>> pages = new ArrayList<>();
    for (String page : rawPages) {
      pages.add(normalizePage(page));
    }
    List<String> rawLines = removeHeaderFooter(pages);
    boolean gapOrFragmentLayout = hasGapOrFragmentLayout(rawLines);

    List<String> cleanLines = collapseLines(rawLines);
    double textQuality = textQuality(String.join("\n", cleanLines));
    List<String> lines = new ArrayList<>(cleanLines.size());
    for (String line : cleanLines) {
      lines.add(CONTROL.matcher(line).replaceAll("").trim());
    }
    String preprocessed = String.join("\n", lines).strip();

    SectionScan scan = scanSections(lines);
    ContactInfo contact = findContact(preprocessed,
        guessFullName(scan.lines().getOrDefault(Section.HEADER, List.of())));
    Map<String, SkillRef> index = skillLookupService.nameIndex();
    List<SkillMatch> skills = new ArrayList<>(matchSkills(lines, scan.skillsLine(), index).values());
    List<ExperienceBlock> blocks = buildExperienceBlocks(
        scan.lines().getOrDefault(Section.EXPERIENCE, List.of()), index);
    List<CvParsedPayload.Education> educations =
        buildEducations(scan.lines().getOrDefault(Section.EDUCATION, List.of()));

    Set<Section> found = EnumSet.noneOf(Section.class);
    found.addAll(scan.lines().keySet());
    if (contact.hasEmail() || contact.hasPhone()) {
      found.add(Section.CONTACT);
    }
    found.retainAll(EXPECTED_SECTIONS);
    double sectionCoverage = found.size() / (double) EXPECTED_SECTIONS.size();
    double contactCompleteness = ((contact.hasEmail() ? 1 : 0) + (contact.hasPhone() ? 1 : 0)) / 2.0;
    long structured = blocks.stream().filter(ExperienceBlock::structured).count();
    double expStructurability = ExtractionMetrics.expStructurability(structured, blocks.size());
    boolean bilingual = scan.viHeadings() >= 2 && scan.enHeadings() >= 2;
    boolean layoutComplex = gapOrFragmentLayout || scan.columnMergedHeadings() > 0 || bilingual;

    ExtractionMetrics metrics = ExtractionMetrics.of(textQuality, sectionCoverage, contactCompleteness,
        ExtractionMetrics.skillYield(skills.size()), expStructurability, layoutComplex);

    Map<Section, String> sections = new EnumMap<>(Section.class);
    scan.lines().forEach((section, sectionLines) -> sections.put(section, String.join("\n", sectionLines)));

    log.info("(extract) deterministic pass: pages={}, lines={}, sections={}, skills={}, expBlocks={}, "
            + "unstructuredBlocks={}, educations={}, T={}, S={}, C={}, K={}, E={}, L={}, Q={}, tookMs={}",
        pages.size(), lines.size(), sections.keySet(), skills.size(), blocks.size(), blocks.size() - structured,
        educations.size(),
        round(metrics.textQuality()), round(metrics.sectionCoverage()), round(metrics.contactCompleteness()),
        round(metrics.skillYield()), round(metrics.expStructurability()), metrics.layoutComplex(),
        round(metrics.q()), (System.nanoTime() - start) / 1_000_000);

    return new DeterministicResult(preprocessed, contact, Collections.unmodifiableMap(sections),
        List.copyOf(blocks), List.copyOf(educations), List.copyOf(skills), metrics);
  }

  // ---------------------------------------------------------------- preprocessing

  /**
   * Splits the concatenated PDF text into pages using the non-whitespace char count of each page
   * (same counting as {@link PdfTextExtractor}). Falls back to form feeds when counts do not fit.
   */
  static List<String> splitPages(PdfText pdfText) {
    String text = pdfText.fullText() == null ? "" : pdfText.fullText();
    int[] counts = pdfText.charsPerPage();
    if (counts == null || counts.length < 2) {
      return List.of(text.split("\f", -1));
    }
    List<String> pages = new ArrayList<>(counts.length);
    int pos = 0;
    for (int page = 0; page < counts.length; page++) {
      if (page == counts.length - 1) {
        pages.add(text.substring(pos));
        break;
      }
      int remaining = counts[page];
      int end = pos;
      while (end < text.length() && remaining > 0) {
        if (!Character.isWhitespace(text.charAt(end))) {
          remaining--;
        }
        end++;
      }
      if (remaining > 0) {
        return List.of(text.split("\f", -1));
      }
      pages.add(text.substring(pos, end));
      pos = end;
    }
    return pages;
  }

  private static List<String> normalizePage(String page) {
    String text = page.replace("\r\n", "\n").replace('\r', '\n');
    text = INVISIBLE.matcher(text).replaceAll("");
    text = Normalizer.normalize(text, Normalizer.Form.NFC);
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      sb.append(c != '\n' && c != '\t' && (Character.isSpaceChar(c) || c == '\u000B' || c == '\f') ? ' ' : c);
    }
    return List.of(sb.toString().split("\n", -1));
  }

  /**
   * Removes page-number lines and header/footer lines repeated across pages. Only the first and
   * last {@value #EDGE_LINES} non-empty lines of each page are considered, and never lines holding a
   * date range / email / phone; for repeated lines the first occurrence is kept (it often holds the
   * candidate's name / contact).
   */
  private static List<String> removeHeaderFooter(List<List<String>> pages) {
    List<Set<Integer>> edges = new ArrayList<>();
    Map<String, Integer> pageCountByKey = new HashMap<>();
    for (List<String> page : pages) {
      Set<Integer> edge = edgeLineIndexes(page);
      edges.add(edge);
      Set<String> keysOnPage = new HashSet<>();
      for (int idx : edge) {
        keysOnPage.add(edgeKey(page.get(idx)));
      }
      keysOnPage.forEach(key -> pageCountByKey.merge(key, 1, Integer::sum));
    }
    int threshold = Math.max(2, (pages.size() + 1) / 2);
    Set<String> seen = new HashSet<>();
    List<String> out = new ArrayList<>();
    for (int p = 0; p < pages.size(); p++) {
      List<String> page = pages.get(p);
      Set<Integer> edge = edges.get(p);
      for (int i = 0; i < page.size(); i++) {
        String line = page.get(i);
        if (edge.contains(i) && !hasProtectedContent(line)) {
          if (PAGE_NUMBER.matcher(WHITESPACE.matcher(fold(line).trim()).replaceAll(" ")).matches()) {
            continue;
          }
          String key = edgeKey(line);
          if (pages.size() >= 2 && pageCountByKey.getOrDefault(key, 0) >= threshold && !seen.add(key)) {
            continue;
          }
        }
        out.add(line);
      }
    }
    return out;
  }

  private static Set<Integer> edgeLineIndexes(List<String> page) {
    List<Integer> nonEmpty = new ArrayList<>();
    for (int i = 0; i < page.size(); i++) {
      if (!page.get(i).isBlank()) {
        nonEmpty.add(i);
      }
    }
    Set<Integer> edge = new LinkedHashSet<>();
    for (int i = 0; i < Math.min(EDGE_LINES, nonEmpty.size()); i++) {
      edge.add(nonEmpty.get(i));
      edge.add(nonEmpty.get(nonEmpty.size() - 1 - i));
    }
    return edge;
  }

  /** Comparison key for repeated header/footer lines: 1–3 digit runs (page numbers) collapse, years stay. */
  private static String edgeKey(String line) {
    return SHORT_NUMBER.matcher(WHITESPACE.matcher(fold(line).trim()).replaceAll(" ")).replaceAll("1");
  }

  /** Lines with a date range, email or phone are never dropped as header/footer/page number. */
  private static boolean hasProtectedContent(String line) {
    return findDateRange(line) != null || EMAIL.matcher(line).find() || PHONE_VN.matcher(line).find()
        || PHONE_INTL.matcher(line).find();
  }

  private static List<String> collapseLines(List<String> rawLines) {
    List<String> out = new ArrayList<>(rawLines.size());
    boolean previousBlank = true;
    for (String raw : rawLines) {
      String line = WHITESPACE.matcher(raw).replaceAll(" ").trim();
      if (line.isEmpty()) {
        if (!previousBlank) {
          out.add("");
        }
        previousBlank = true;
      } else {
        out.add(line);
        previousBlank = false;
      }
    }
    while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  private static boolean hasGapOrFragmentLayout(List<String> rawLines) {
    int nonEmpty = 0;
    int gapLines = 0;
    int shortLines = 0;
    for (String raw : rawLines) {
      String line = raw.strip();
      if (line.isEmpty()) {
        continue;
      }
      nonEmpty++;
      if (INTERNAL_GAP.matcher(line).find()) {
        gapLines++;
      }
      if (line.length() <= 3 && line.codePoints().anyMatch(Character::isLetterOrDigit)) {
        shortLines++;
      }
    }
    if (nonEmpty < MIN_LINES_FOR_RATIO) {
      return false;
    }
    return gapLines > GAP_LINE_RATIO * nonEmpty || shortLines > SHORT_LINE_RATIO * nonEmpty;
  }

  // ---------------------------------------------------------------- text quality

  /**
   * T = 1 - garbage / total over non-whitespace code points. Garbage: U+FFFD, control chars,
   * private-use / unassigned code points, Latin-1 symbols typical of mojibake, and symbols standing
   * alone between whitespace (except common bullets).
   */
  static double textQuality(String text) {
    int total = 0;
    int garbage = 0;
    int[] cps = text.codePoints().toArray();
    for (int i = 0; i < cps.length; i++) {
      int cp = cps[i];
      if (Character.isWhitespace(cp)) {
        continue;
      }
      total++;
      if (isGarbage(cps, i)) {
        garbage++;
      }
    }
    return total == 0 ? 0.0 : 1.0 - garbage / (double) total;
  }

  private static boolean isGarbage(int[] cps, int i) {
    int cp = cps[i];
    if (cp == 0xFFFD || Character.isISOControl(cp)) {
      return true;
    }
    int type = Character.getType(cp);
    if (type == Character.PRIVATE_USE || type == Character.UNASSIGNED || type == Character.SURROGATE) {
      return true;
    }
    if (cp >= 0x80 && cp <= 0xBF && NON_GARBAGE_LATIN1.indexOf(cp) < 0) {
      return true;
    }
    boolean symbol = type == Character.MATH_SYMBOL || type == Character.OTHER_SYMBOL
        || type == Character.MODIFIER_SYMBOL;
    if (symbol && NON_GARBAGE_SYMBOLS.indexOf(cp) < 0) {
      boolean leftAlone = i == 0 || Character.isWhitespace(cps[i - 1]);
      boolean rightAlone = i == cps.length - 1 || Character.isWhitespace(cps[i + 1]);
      return leftAlone && rightAlone;
    }
    return false;
  }

  // ---------------------------------------------------------------- contact

  private static ContactInfo findContact(String text, String fullName) {
    Set<String> emails = new LinkedHashSet<>();
    Set<String> links = new LinkedHashSet<>();
    StringBuilder masked = new StringBuilder(text);
    Matcher email = EMAIL.matcher(text);
    while (email.find()) {
      emails.add(email.group());
      mask(masked, email.start(), email.end());
    }
    Matcher url = URL.matcher(masked.toString());
    while (url.find()) {
      String link = url.group().replaceAll("[.,:;!?'\"]+$", "");
      if (!link.isEmpty()) {
        links.add(link);
      }
      mask(masked, url.start(), url.end());
    }
    Set<String> phones = new LinkedHashSet<>();
    String phoneText = masked.toString();
    Matcher vn = PHONE_VN.matcher(phoneText);
    while (vn.find()) {
      String digits = vn.group().replaceAll("\\D", "");
      String national = digits.startsWith("84") && vn.group().contains("84") && !vn.group().startsWith("0")
          && !vn.group().startsWith("(0") ? "0" + digits.substring(2) : digits;
      if (national.startsWith("0") && (national.length() == 10 || national.length() == 11)) {
        phones.add(vn.group().trim());
        mask(masked, vn.start(), vn.end());
      }
    }
    Matcher intl = PHONE_INTL.matcher(masked.toString());
    while (intl.find()) {
      int digits = intl.group().replaceAll("\\D", "").length();
      if (digits >= 8 && digits <= 15) {
        phones.add(intl.group().trim());
      }
    }
    return new ContactInfo(fullName, List.copyOf(emails), List.copyOf(phones), List.copyOf(links));
  }

  /**
   * Name guess: among the first {@value #NAME_SCAN_LINES} lines before any heading, the first one
   * with 2–6 words made only of letters (plus . ' -), each word capitalized, and no job-title keyword.
   */
  static String guessFullName(List<String> headerLines) {
    for (int i = 0; i < Math.min(NAME_SCAN_LINES, headerLines.size()); i++) {
      String line = headerLines.get(i).trim();
      if (!FULL_NAME.matcher(line).matches() || TITLE_KEYWORD.matcher(fold(line)).find()) {
        continue;
      }
      String[] words = line.split(" ");
      boolean capitalized = true;
      for (String word : words) {
        capitalized &= Character.isUpperCase(word.charAt(0));
      }
      if (capitalized && words.length >= 2 && words.length <= 6) {
        return line;
      }
    }
    return null;
  }

  private static void mask(StringBuilder sb, int start, int end) {
    for (int i = start; i < end; i++) {
      sb.setCharAt(i, ' ');
    }
  }

  // ---------------------------------------------------------------- sections

  private record Heading(Section section, boolean vi) {
  }

  private record HeadingMatch(Section section, boolean vi, boolean columnMerged, String inline) {
  }

  /** {@code skillsLine[i]} is true when {@code lines.get(i)} holds content of the SKILLS section. */
  private record SectionScan(Map<Section, List<String>> lines, int viHeadings, int enHeadings,
                             int columnMergedHeadings, boolean[] skillsLine) {
  }

  private static SectionScan scanSections(List<String> lines) {
    Map<Section, List<String>> bySection = new EnumMap<>(Section.class);
    Set<Section> viSeen = EnumSet.noneOf(Section.class);
    Set<Section> enSeen = EnumSet.noneOf(Section.class);
    int columnMerged = 0;
    boolean[] skillsLine = new boolean[lines.size()];
    Section current = Section.HEADER;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isEmpty()) {
        continue;
      }
      HeadingMatch heading = detectHeading(line);
      if (heading == null) {
        bySection.computeIfAbsent(current, s -> new ArrayList<>()).add(line);
        skillsLine[i] = current == Section.SKILLS;
        continue;
      }
      current = heading.section();
      bySection.computeIfAbsent(current, s -> new ArrayList<>());
      (heading.vi() ? viSeen : enSeen).add(current);
      if (heading.columnMerged()) {
        columnMerged++;
      }
      if (heading.inline() != null) {
        bySection.get(current).add(heading.inline());
        skillsLine[i] = current == Section.SKILLS;
      }
    }
    return new SectionScan(bySection, viSeen.size(), enSeen.size(), columnMerged, skillsLine);
  }

  /** Returns the heading on this line, or null. Case- and diacritic-insensitive. */
  private static HeadingMatch detectHeading(String line) {
    String trimmed = line.trim();
    int colon = trimmed.indexOf(':');
    if (colon > 0 && colon < trimmed.length() - 1) {
      HeadingMatch head = detectHeadingOnly(trimmed.substring(0, colon));
      String rest = trimmed.substring(colon + 1).trim();
      if (head != null && !head.columnMerged() && !rest.isEmpty()) {
        return new HeadingMatch(head.section(), head.vi(), false, rest);
      }
      return null;
    }
    return detectHeadingOnly(trimmed);
  }

  private static HeadingMatch detectHeadingOnly(String text) {
    String trimmed = text.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_HEADING_LENGTH || trimmed.indexOf('@') >= 0) {
      return null;
    }
    String stripped = HEADING_NUMBERING.matcher(trimmed).replaceFirst("");
    stripped = LEADING_SYMBOLS.matcher(stripped).replaceFirst("");
    String folded = fold(stripped);
    String norm = folded.replaceAll("[^a-z0-9]+", " ").trim();
    if (norm.isEmpty()) {
      return null;
    }
    String[] words = norm.split(" ");
    if (words.length > MAX_HEADING_WORDS) {
      return null;
    }
    Heading exact = HEADINGS.get(norm);
    if (exact != null) {
      return new HeadingMatch(exact.section(), exact.vi(), false, null);
    }
    for (int i = 1; i < words.length; i++) {
      Heading prefix = HEADINGS.get(String.join(" ", List.of(words).subList(0, i)));
      if (prefix == null) {
        continue;
      }
      int restStart = i;
      if (words[restStart].equals("and") || words[restStart].equals("va")) {
        restStart++;
      }
      if (restStart >= words.length) {
        continue;
      }
      Heading rest = HEADINGS.get(String.join(" ", List.of(words).subList(restStart, words.length)));
      if (rest != null) {
        boolean connector = HEADING_CONNECTOR.matcher(folded).find();
        return new HeadingMatch(prefix.section(), prefix.vi(), !connector && rest.section() != prefix.section(),
            null);
      }
    }
    return null;
  }

  private static Map<String, Heading> buildHeadings() {
    Map<String, Heading> map = new HashMap<>();
    addHeadings(map, Section.EXPERIENCE, true, "kinh nghiem", "kinh nghiem lam viec", "kinh nghiem chuyen mon",
        "kinh nghiem lam viec thuc te", "qua trinh lam viec", "qua trinh cong tac", "lich su lam viec");
    addHeadings(map, Section.EXPERIENCE, false, "experience", "experiences", "work experience",
        "working experience", "professional experience", "employment history", "employment", "work history",
        "career history", "relevant experience");
    addHeadings(map, Section.EDUCATION, true, "hoc van", "trinh do hoc van", "qua trinh hoc tap", "hoc tap",
        "dao tao", "trinh do dao tao", "trinh do");
    addHeadings(map, Section.EDUCATION, false, "education", "educations", "academic background",
        "education background", "educational background", "academic history");
    addHeadings(map, Section.SKILLS, true, "ky nang", "cac ky nang", "ky nang chuyen mon", "ky nang mem",
        "ky nang lap trinh", "ky nang ky thuat", "cong nghe", "cong nghe su dung");
    addHeadings(map, Section.SKILLS, false, "skills", "skill", "technical skills", "hard skills", "soft skills",
        "key skills", "core skills", "skills summary", "competencies", "core competencies", "technologies",
        "tech stack", "technical stack", "programming skills", "professional skills", "it skills");
    addHeadings(map, Section.CONTACT, true, "lien he", "thong tin lien he", "thong tin ca nhan", "lien lac",
        "thong tin lien lac");
    addHeadings(map, Section.CONTACT, false, "contact", "contacts", "contact information", "contact info",
        "contact details", "personal information", "personal info", "personal details");
    addHeadings(map, Section.PROJECTS, true, "du an", "cac du an", "du an tieu bieu", "du an da tham gia",
        "du an ca nhan", "du an da thuc hien");
    addHeadings(map, Section.PROJECTS, false, "projects", "project", "personal projects", "key projects",
        "project experience", "academic projects", "side projects", "notable projects");
    addHeadings(map, Section.CERTIFICATIONS, true, "chung chi", "chung nhan", "cac chung chi", "bang cap");
    addHeadings(map, Section.CERTIFICATIONS, false, "certifications", "certification", "certificates",
        "certificate", "licenses certifications", "licenses and certifications");
    addHeadings(map, Section.AWARDS, true, "giai thuong", "thanh tich", "danh hieu", "giai thuong thanh tich");
    addHeadings(map, Section.AWARDS, false, "awards", "honors", "honors awards", "achievements", "award");
    addHeadings(map, Section.LANGUAGES, true, "ngon ngu", "ngoai ngu", "trinh do ngoai ngu");
    addHeadings(map, Section.LANGUAGES, false, "languages", "language", "language skills",
        "language proficiency");
    addHeadings(map, Section.ACTIVITIES, true, "hoat dong", "hoat dong ngoai khoa", "hoat dong xa hoi");
    addHeadings(map, Section.ACTIVITIES, false, "activities", "extracurricular activities", "volunteer",
        "volunteering", "volunteer experience");
    addHeadings(map, Section.INTERESTS, true, "so thich");
    addHeadings(map, Section.INTERESTS, false, "interests", "hobbies", "hobbies interests");
    addHeadings(map, Section.REFERENCES, true, "nguoi tham chieu", "tham chieu", "nguoi gioi thieu");
    addHeadings(map, Section.REFERENCES, false, "references", "reference");
    addHeadings(map, Section.SUMMARY, true, "muc tieu", "muc tieu nghe nghiep", "muc tieu cong viec",
        "gioi thieu", "gioi thieu ban than", "tom tat", "tong quan", "dinh huong nghe nghiep");
    addHeadings(map, Section.SUMMARY, false, "summary", "professional summary", "career objective",
        "career objectives", "objective", "objectives", "profile", "about me", "career summary", "about",
        "professional profile");
    return Collections.unmodifiableMap(map);
  }

  private static void addHeadings(Map<String, Heading> map, Section section, boolean vi, String... phrases) {
    for (String phrase : phrases) {
      map.put(phrase, new Heading(section, vi));
    }
  }

  // ---------------------------------------------------------------- date ranges

  /** Finds the first valid date range in the line, or null. */
  static DateRange findDateRange(String line) {
    Matcher m = DATE_RANGE.matcher(fold(line));
    while (m.find()) {
      String[] startDate = parseDate(m.group(1).trim());
      String endText = m.group(2).trim();
      boolean current = PRESENT_ONLY.matcher(endText).matches();
      String[] endDate = current ? null : parseDate(endText);
      if (startDate != null && (current || endDate != null)) {
        return new DateRange(format(startDate), current ? null : format(endDate), current,
            line.substring(m.start(), m.end()));
      }
    }
    return null;
  }

  private static Matcher findDateRangeMatcher(String line) {
    Matcher m = DATE_RANGE.matcher(fold(line));
    while (m.find()) {
      if (parseDate(m.group(1).trim()) != null) {
        return m;
      }
    }
    return null;
  }

  /** Returns {year, month-or-null}, or null when invalid. Input is folded text. */
  private static String[] parseDate(String text) {
    Matcher m = DATE_THANG.matcher(text);
    if (!m.matches()) {
      m = DATE_NUM.matcher(text);
    }
    if (m.matches()) {
      int month = Integer.parseInt(m.group(1));
      return month >= 1 && month <= 12 ? new String[] {m.group(2), String.format("%02d", month)} : null;
    }
    m = DATE_NAME.matcher(text);
    if (m.matches()) {
      int month = monthFromName(m.group(1));
      return new String[] {m.group(2), String.format("%02d", month)};
    }
    m = DATE_YEAR.matcher(text);
    return m.matches() ? new String[] {m.group(1), null} : null;
  }

  private static int monthFromName(String name) {
    String prefix = name.substring(0, 3);
    List<String> months = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov",
        "dec");
    return months.indexOf(prefix) + 1;
  }

  private static String format(String[] date) {
    return date[1] == null ? date[0] : date[0] + "-" + date[1];
  }

  // ---------------------------------------------------------------- experience blocks

  /**
   * Splits the experience section into blocks anchored on date-range lines. Title / company are
   * looked for in up to {@value #MAX_HEADER_LINES} header-like lines before the date line
   * ("Title at Company" then date), in the rest of the date line ("Company | date"), and — when
   * nothing was found before the date, or only one of title / company was — in up to
   * {@value #MAX_HEADER_LINES} header-like lines right after it ("date" then "Title at Company",
   * "Company | date" then "Title"). Lines taken after a date belong to that block and are never
   * used as the header of the next one; once a block used lines after its date, the next block
   * looks after its date first. A block that still has neither title nor company stays in
   * {@link DeterministicResult#experienceBlocks()} with {@code structured=false}.
   */
  private List<ExperienceBlock> buildExperienceBlocks(List<String> lines, Map<String, SkillRef> index) {
    List<Integer> dateLines = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (isDateAnchor(lines.get(i))) {
        dateLines.add(i);
      }
    }
    if (dateLines.isEmpty()) {
      return List.of();
    }
    int count = dateLines.size();
    int[] headerStarts = new int[count];
    int[] headerEnds = new int[count];
    int claimedUntil = -1;
    boolean preferAfter = false;
    for (int b = 0; b < count; b++) {
      int dateLine = dateLines.get(b);
      int limit = b + 1 < count ? reservedLimit(lines, dateLine, dateLines.get(b + 1)) : lines.size();
      HeaderFields remainder = classify(remainderCandidates(lines.get(dateLine)));
      int after = preferAfter && remainder.isEmpty() ? lookAhead(lines, dateLine, limit, null) : dateLine;
      int before = dateLine;
      if (after == dateLine) {
        before = lookBack(lines, dateLine, claimedUntil);
        HeaderFields found = classify(candidates(lines, before, dateLine, dateLine));
        if (found.isEmpty()) {
          after = lookAhead(lines, dateLine, limit, null);
        } else if (found.title() == null || found.company() == null) {
          after = lookAhead(lines, dateLine, Math.min(limit, dateLine + 2),
              found.title() == null ? TITLE_KEYWORD : COMPANY_KEYWORD);
        }
      }
      headerStarts[b] = before;
      headerEnds[b] = after;
      preferAfter = after > dateLine && before == dateLine;
      claimedUntil = after;
    }
    List<ExperienceBlock> blocks = new ArrayList<>();
    for (int b = 0; b < count; b++) {
      int from = b == 0 ? 0 : headerStarts[b];
      int to = b + 1 < count ? headerStarts[b + 1] : lines.size();
      blocks.add(buildBlock(lines.subList(from, to), headerStarts[b] - from, dateLines.get(b) - from,
          headerEnds[b] - from, index));
    }
    return blocks;
  }

  /**
   * End (exclusive) of the lookahead window of the block anchored at {@code dateLine}. Normally the
   * next date line; one line earlier when the line right before the next date holds both a title and
   * a company ("Senior Dev — Beta Tech") and the next date line is not itself followed by a
   * header-like line — that line is the next block's header (title-then-date layout), not ours.
   */
  private static int reservedLimit(List<String> lines, int dateLine, int nextDateLine) {
    int candidate = nextDateLine - 1;
    if (candidate <= dateLine || !isHeaderLike(lines.get(candidate))) {
      return nextDateLine;
    }
    boolean nextIsDateFirst = nextDateLine + 1 < lines.size() && isHeaderLike(lines.get(nextDateLine + 1));
    List<String> parts = new ArrayList<>();
    addCandidates(parts, lines.get(candidate));
    HeaderFields fields = classify(parts);
    return !nextIsDateFirst && fields.title() != null && fields.company() != null ? candidate : nextDateLine;
  }

  /**
   * A line with a date range anchors a block. A bullet line anchors only when the date range opens
   * it ("• 2022 – 2023 | Engineer | FPT") or when, without the date and separators, it is short
   * (at most {@value #MAX_ANCHOR_BULLET_WORDS} words); a description bullet that mentions a date
   * range mid-sentence ("- Led the migration from 2019 - 2020 across ...") does not.
   */
  static boolean isDateAnchor(String line) {
    if (findDateRange(line) == null) {
      return false;
    }
    if (!isBullet(line)) {
      return true;
    }
    String content = stripBullet(line);
    Matcher m = findDateRangeMatcher(content);
    if (m == null) {
      return false;
    }
    if (m.start() <= MAX_ANCHOR_DATE_OFFSET) {
      return true;
    }
    String rest = content.substring(0, m.start()) + " " + content.substring(m.end());
    long words = WHITESPACE.splitAsStream(rest.replaceAll("[-–—|:•·,]", " ").trim())
        .filter(word -> !word.isEmpty())
        .count();
    return words <= MAX_ANCHOR_BULLET_WORDS;
  }

  private static String stripBullet(String line) {
    return BULLET.matcher(line.trim()).replaceFirst("").trim();
  }

  /** First index of the header-like lines right before {@code dateLine}, after {@code claimedUntil}. */
  private static int lookBack(List<String> lines, int dateLine, int claimedUntil) {
    int start = dateLine;
    for (int k = 1; k <= MAX_HEADER_LINES; k++) {
      int j = dateLine - k;
      if (j <= claimedUntil || !isHeaderLike(lines.get(j))) {
        break;
      }
      start = j;
    }
    return start;
  }

  /**
   * Last index of the header-like lines right after {@code dateLine} (before {@code limit}), or
   * {@code dateLine} when none. With {@code required}, only lines matching that keyword are taken.
   */
  private static int lookAhead(List<String> lines, int dateLine, int limit, Pattern required) {
    int end = dateLine;
    for (int j = dateLine + 1; j < limit && j <= dateLine + MAX_HEADER_LINES; j++) {
      String line = lines.get(j);
      if (!isHeaderLike(line) || required != null && !required.matcher(fold(line)).find()) {
        break;
      }
      end = j;
      List<String> taken = new ArrayList<>();
      for (int i = dateLine + 1; i <= end; i++) {
        addCandidates(taken, lines.get(i));
      }
      HeaderFields fields = classify(taken);
      if (fields.title() != null && fields.company() != null) {
        break;
      }
    }
    return end;
  }

  private static String dateRemainder(String dateLine) {
    Matcher m = findDateRangeMatcher(dateLine);
    return m == null ? dateLine : dateLine.substring(0, m.start()) + " | " + dateLine.substring(m.end());
  }

  private static List<String> remainderCandidates(String dateLine) {
    List<String> out = new ArrayList<>();
    addCandidates(out, dateRemainder(isBullet(dateLine) ? stripBullet(dateLine) : dateLine));
    return out;
  }

  /** Candidates from lines [{@code from}, {@code dateIdx}), the date line remainder and ({@code dateIdx}, {@code to}]. */
  private static List<String> candidates(List<String> lines, int from, int dateIdx, int to) {
    List<String> out = new ArrayList<>();
    for (int i = from; i < dateIdx; i++) {
      addCandidates(out, lines.get(i));
    }
    out.addAll(remainderCandidates(lines.get(dateIdx)));
    for (int i = dateIdx + 1; i <= to; i++) {
      addCandidates(out, lines.get(i));
    }
    return out;
  }

  private record HeaderFields(String title, String company) {

    boolean isEmpty() {
      return title == null && company == null;
    }
  }

  /** Keyword match first (title / company), then unknown candidates fill title, then company. */
  private static HeaderFields classify(List<String> candidates) {
    String title = null;
    String company = null;
    List<String> unknown = new ArrayList<>();
    for (String candidate : candidates) {
      String folded = fold(candidate);
      if (title == null && TITLE_KEYWORD.matcher(folded).find()) {
        title = candidate;
      } else if (company == null && COMPANY_KEYWORD.matcher(folded).find()) {
        company = candidate;
      } else {
        unknown.add(candidate);
      }
    }
    for (String candidate : unknown) {
      if (title == null) {
        title = candidate;
      } else if (company == null) {
        company = candidate;
      }
    }
    return new HeaderFields(title, company);
  }

  private ExperienceBlock buildBlock(List<String> blockLines, int headerStart, int dateIdx, int headerEnd,
                                     Map<String, SkillRef> index) {
    DateRange range = findDateRange(blockLines.get(dateIdx));
    HeaderFields fields = classify(candidates(blockLines, headerStart, dateIdx, headerEnd));
    String title = fields.title();
    String company = fields.company();
    List<String> descriptionLines = new ArrayList<>();
    for (int i = 0; i < blockLines.size(); i++) {
      if (i < headerStart || i > headerEnd) {
        descriptionLines.add(blockLines.get(i));
      }
    }
    List<String> skillNames = matchSkills(blockLines, new boolean[blockLines.size()], index).values().stream()
        .map(match -> match.skill().name())
        .toList();
    boolean structured = range != null && (title != null || company != null);
    return new ExperienceBlock(List.copyOf(blockLines), range, title, company,
        descriptionLines.isEmpty() ? null : String.join("\n", descriptionLines), skillNames, structured);
  }

  // ---------------------------------------------------------------- education

  /**
   * Education entries of the EDUCATION section. Each line naming a school (university, đại học,
   * college, học viện, trường…) starts an entry; when there is none, lines with a degree keyword do.
   * An entry takes one unclaimed header-like line before its anchor (degree written above the school)
   * and up to {@value #MAX_HEADER_LINES} non-bullet lines after it, stopping at a second degree or
   * date. Lines are split like experience headers (plus commas): the part with a school keyword is
   * the school, the part with a degree keyword ("B.Sc.", "Bachelor of Science", "Kỹ sư", "Cử nhân")
   * is the degree and its remainder ("Computer Science", "in X", "ngành X") the major; a "Major: X" /
   * "Chuyên ngành: X" part is the major. Dates: first date range, else the last single year as the
   * end date. Same date format as experiences ("YYYY-MM" or "YYYY"); "present" gives a null end date.
   */
  static List<CvParsedPayload.Education> buildEducations(List<String> lines) {
    List<Integer> anchors = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (!isBullet(lines.get(i)) && SCHOOL_KEYWORD.matcher(fold(lines.get(i))).find()) {
        anchors.add(i);
      }
    }
    if (anchors.isEmpty()) {
      for (int i = 0; i < lines.size(); i++) {
        if (!isBullet(lines.get(i)) && DEGREE.matcher(fold(lines.get(i))).find()) {
          anchors.add(i);
        }
      }
    }
    List<CvParsedPayload.Education> out = new ArrayList<>();
    int claimedUntil = -1;
    for (int a = 0; a < anchors.size(); a++) {
      int anchor = anchors.get(a);
      int limit = a + 1 < anchors.size() ? anchors.get(a + 1) : lines.size();
      List<String> entry = new ArrayList<>();
      int before = anchor - 1;
      if (before > claimedUntil && isHeaderLike(lines.get(before))
          && DEGREE.matcher(fold(lines.get(before))).find()) {
        entry.add(lines.get(before));
      }
      entry.add(lines.get(anchor));
      int last = anchor;
      for (int j = anchor + 1; j < limit && j <= anchor + MAX_HEADER_LINES; j++) {
        String line = lines.get(j);
        String folded = fold(line);
        boolean hasDegree = entry.stream().anyMatch(l -> DEGREE.matcher(fold(l)).find());
        boolean hasDate = entry.stream().anyMatch(l -> YEAR_TOKEN.matcher(fold(l)).find());
        if (line.isEmpty() || isBullet(line) || line.length() > MAX_HEADER_LINE_LENGTH
            || hasDegree && DEGREE.matcher(folded).find() || hasDate && YEAR_TOKEN.matcher(folded).find()) {
          break;
        }
        entry.add(line);
        last = j;
      }
      claimedUntil = last;
      CvParsedPayload.Education education = toEducation(entry);
      if (education != null) {
        out.add(education);
      }
    }
    return out;
  }

  private static CvParsedPayload.Education toEducation(List<String> entry) {
    String startDate = null;
    String endDate = null;
    List<String> candidates = new ArrayList<>();
    for (String line : entry) {
      String text = line;
      DateRange range = startDate == null && endDate == null ? findDateRange(line) : null;
      if (range != null) {
        startDate = range.startDate();
        endDate = range.current() ? null : range.endDate();
        text = dateRemainder(line);
      } else {
        Matcher year = YEAR_TOKEN.matcher(fold(line));
        String lastYear = null;
        while (year.find()) {
          lastYear = year.group();
        }
        if (lastYear != null && startDate == null && endDate == null) {
          endDate = lastYear;
        }
        text = YEAR_TOKEN.matcher(text).replaceAll(" | ");
      }
      for (String part : text.split(",")) {
        addCandidates(candidates, part);
      }
    }
    String school = null;
    String degree = null;
    String major = null;
    List<String> unknown = new ArrayList<>();
    for (String candidate : candidates) {
      String folded = fold(candidate);
      Matcher degreeMatch = DEGREE.matcher(folded);
      boolean degreeFound = degreeMatch.find();
      if (school == null && SCHOOL_KEYWORD.matcher(folded).find()) {
        if (degree == null && degreeFound && degreeMatch.start() == 0) {
          degree = candidate.substring(0, degreeMatch.end()).trim();
          school = candidate.substring(degreeMatch.end()).trim();
        } else {
          school = candidate;
        }
      } else if (degree == null && degreeFound) {
        degree = candidate.substring(degreeMatch.start(), degreeMatch.end()).trim();
        String rest = candidate.substring(degreeMatch.end());
        Matcher prefix = MAJOR_PREFIX.matcher(fold(rest));
        rest = (prefix.find() ? rest.substring(prefix.end()) : rest).trim();
        if (major == null && rest.codePoints().anyMatch(Character::isLetter)) {
          major = rest;
        }
      } else if (MAJOR_LABEL.matcher(folded).find()) {
        Matcher prefix = MAJOR_PREFIX.matcher(folded);
        String rest = (prefix.find() ? candidate.substring(prefix.end()) : candidate).trim();
        if (major == null && !rest.isEmpty()) {
          major = rest;
        }
      } else if (!GRADE.matcher(folded).find()) {
        unknown.add(candidate);
      }
    }
    if (major == null && degree != null && !unknown.isEmpty()) {
      major = unknown.get(0);
    }
    if (school == null && degree == null) {
      return null;
    }
    return new CvParsedPayload.Education(school, degree, major, startDate, endDate);
  }

  private static void addCandidates(List<String> out, String text) {
    for (String part : CANDIDATE_SPLIT.split(text)) {
      String candidate = part.replaceAll("^[\\s|,:;()\\[\\]\\-–—•·]+|[\\s|,:;()\\[\\]\\-–—•·]+$", "");
      if (!candidate.isEmpty() && candidate.length() <= MAX_CANDIDATE_LENGTH
          && candidate.codePoints().anyMatch(Character::isLetter)) {
        out.add(candidate);
      }
    }
  }

  private static boolean isHeaderLike(String line) {
    boolean sentence = line.endsWith(".") && line.split(" ").length > MAX_HEADER_WORDS_ENDING_WITH_DOT;
    return !line.isEmpty() && line.length() <= MAX_HEADER_LINE_LENGTH && !isBullet(line) && !sentence
        && findDateRange(line) == null;
  }

  private static boolean isBullet(String line) {
    return BULLET.matcher(line).find();
  }

  // ---------------------------------------------------------------- skill gazetteer

  private record Span(int start, int end, String text) {
  }

  /**
   * Gazetteer match over lines: whole chunks containing separators (e.g. "CI/CD"), then greedy
   * longest 3/2/1-token n-grams of whitespace-adjacent tokens. Chunks and n-grams are cleaned first,
   * then keyed with {@link SkillUtils#normalizeSkill} like {@link SkillLookupService#nameIndex()}.
   * Single-char tokens only match when written in uppercase ("C", "R"); digit-only tokens never match.
   * Skills with a name, or matched text (e.g. an alias), of at most {@value #SHORT_SKILL_LENGTH}
   * chars ("Go", "R") only match with the exact case of the skill name, or anywhere inside the
   * SKILLS section. Matches touching {@code &} ("R&D") are rejected.
   *
   * @param skillsLine {@code skillsLine[i]} true when line {@code i} is in the SKILLS section
   */
  static Map<UUID, SkillMatch> matchSkills(List<String> lines, boolean[] skillsLine, Map<String, SkillRef> index) {
    Map<UUID, SkillMatch> matches = new LinkedHashMap<>();
    if (index == null || index.isEmpty()) {
      return matches;
    }
    for (int li = 0; li < lines.size(); li++) {
      String line = lines.get(li);
      boolean inSkills = li < skillsLine.length && skillsLine[li];
      if (line.isBlank()) {
        continue;
      }
      List<Span> tokens = spans(TOKEN.matcher(line));
      boolean[] consumed = new boolean[tokens.size()];
      int pos = 0;
      for (String chunk : CHUNK_SPLIT.split(line)) {
        int chunkStart = line.indexOf(chunk, pos);
        if (chunk.isEmpty() || chunkStart < 0) {
          continue;
        }
        pos = chunkStart + chunk.length();
        String cleaned = chunk.replaceAll("^[.:!?'\"]+|[.:!?'\"]+$", "");
        if (cleaned.isEmpty() || TOKEN.matcher(cleaned).matches()) {
          continue;
        }
        SkillRef ref = index.get(SkillUtils.normalizeSkill(cleaned));
        if (ref != null && acceptable(ref, cleaned, inSkills)) {
          addMatch(matches, ref, cleaned, line);
          for (int t = 0; t < tokens.size(); t++) {
            if (tokens.get(t).start() >= chunkStart && tokens.get(t).end() <= pos) {
              consumed[t] = true;
            }
          }
        }
      }
      for (int t = 0; t < tokens.size(); t++) {
        if (consumed[t]) {
          continue;
        }
        for (int n = Math.min(3, tokens.size() - t); n >= 1; n--) {
          if (!adjacent(line, tokens, consumed, t, n)) {
            continue;
          }
          int gramStart = tokens.get(t).start();
          int gramEnd = tokens.get(t + n - 1).end();
          String gram = line.substring(gramStart, gramEnd);
          if (n == 1 && !singleTokenEligible(gram) || touchesAmpersand(line, gramStart, gramEnd)) {
            continue;
          }
          String key = SkillUtils.normalizeSkill(WHITESPACE.matcher(gram).replaceAll(" "));
          SkillRef ref = index.get(key);
          if (ref != null && acceptable(ref, gram, inSkills)) {
            addMatch(matches, ref, gram, line);
            for (int k = t; k < t + n; k++) {
              consumed[k] = true;
            }
            t += n - 1;
            break;
          }
        }
      }
    }
    return matches;
  }

  private static List<Span> spans(Matcher matcher) {
    List<Span> spans = new ArrayList<>();
    while (matcher.find()) {
      spans.add(new Span(matcher.start(), matcher.end(), matcher.group()));
    }
    return spans;
  }

  private static boolean adjacent(String line, List<Span> tokens, boolean[] consumed, int from, int n) {
    for (int k = from; k < from + n; k++) {
      if (consumed[k]) {
        return false;
      }
      if (k > from && !line.substring(tokens.get(k - 1).end(), tokens.get(k).start()).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private static boolean touchesAmpersand(String line, int start, int end) {
    return start > 0 && line.charAt(start - 1) == '&' || end < line.length() && line.charAt(end) == '&';
  }

  private static boolean acceptable(SkillRef ref, String matched, boolean inSkills) {
    String name = ref.name().trim();
    String surface = matched.trim();
    return inSkills || name.length() > SHORT_SKILL_LENGTH && surface.length() > SHORT_SKILL_LENGTH
        || name.equals(surface);
  }

  private static boolean singleTokenEligible(String token) {
    if (token.chars().allMatch(Character::isDigit)) {
      return false;
    }
    return token.length() > 1 || Character.isUpperCase(token.charAt(0));
  }

  private static void addMatch(Map<UUID, SkillMatch> matches, SkillRef ref, String matched, String line) {
    String evidence = line.length() > MAX_EVIDENCE_LENGTH ? line.substring(0, MAX_EVIDENCE_LENGTH) : line;
    matches.putIfAbsent(ref.id(), new SkillMatch(ref, matched, evidence));
  }

  // ---------------------------------------------------------------- helpers

  /**
   * Lowercases and strips diacritics char by char (đ → d), keeping the string length so that
   * match offsets on the folded text are valid on the original.
   */
  static String fold(String text) {
    char[] out = new char[text.length()];
    for (int i = 0; i < text.length(); i++) {
      char c = Character.toLowerCase(text.charAt(i));
      if (c == 'đ') {
        c = 'd';
      } else if (c > 127 && !Character.isSurrogate(c)) {
        String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        if (!decomposed.isEmpty() && decomposed.charAt(0) < 128) {
          c = decomposed.charAt(0);
        }
      }
      out[i] = c;
    }
    return new String(out);
  }

  private static double round(double value) {
    return Math.round(value * 1000) / 1000.0;
  }
}
