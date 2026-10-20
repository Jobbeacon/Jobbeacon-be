package com.jobbeacon.service.impl;

import com.jobbeacon.client.CvExtractionHints;
import com.jobbeacon.client.CvModelResult;
import com.jobbeacon.client.GeminiCvExtractionClient;
import com.jobbeacon.cv.CvPdfContent;
import com.jobbeacon.cv.CvPdfReader;
import com.jobbeacon.cv.CvPostProcessResult;
import com.jobbeacon.cv.CvPostProcessor;
import com.jobbeacon.cv.DeterministicExtractor;
import com.jobbeacon.cv.DeterministicResult;
import com.jobbeacon.cv.ExtractionMetrics;
import com.jobbeacon.cv.PdfText;
import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.dto.cv.CvParsedPayload.SkillItem;
import com.jobbeacon.dto.skill.SkillRef;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.exception.CvExtractionException;
import com.jobbeacon.service.CvExtractionService;
import com.jobbeacon.service.CvProfileService;
import com.jobbeacon.service.CvProfileService.CvProfileView;
import com.jobbeacon.service.SkillResolutionService;
import com.jobbeacon.utils.SkillUtils;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates the CV extraction pipeline (analysis doc §4, §7.3, §8.1):
 * read PDF (with timeout) → scan detection → deterministic pass → decision tree → model (optional)
 * → post-process → final quality check → skill resolution → save profile (D-007, D-008).
 *
 * <p>Only the profile write ({@link CvProfileService#saveParsed}) runs in a transaction; PDF parsing
 * and model calls never do. Every failure throws {@link CvExtractionException} and writes nothing.
 * Logs contain ids, branch, scores, counts, timings and token usage only — never CV content.
 *
 * <p>Branches ({@code branch} in logs):
 * <ul>
 *   <li>{@code scan_low_text}: average chars/page below {@code app.cv.ocr-min-chars-per-page} →
 *       Gemini PDF mode ({@code usedOcr}, {@code usedModel}); model disabled → {@code cv_unreadable};
 *       model error → that model error.</li>
 *   <li>{@code scan_low_t}: deterministic T below {@code t-min} → same as scan (once, no loop).</li>
 *   <li>{@code model_text}: layout complex, a dated experience block without title / company,
 *       E below {@code e-min} or Q below {@code q-high} →
 *       Gemini text mode with deterministic hints. Model disabled or TRANSIENT error → fall back
 *       to the deterministic result when its Q ≥ {@code q-low} (D-006, saved as
 *       {@code deterministic-v1}), else the model error ({@code cv_model_error} when disabled).
 *       PERMANENT model errors are thrown as is.</li>
 *   <li>{@code keyword_only}: Q ≥ {@code q-high} → deterministic result only, no model call.</li>
 * </ul>
 *
 * <p>The final quality score is recomputed on the post-processed payload (candidate skills, before
 * resolution) with the §7.2 weights (see {@link #finalQuality}); below {@code q-low}, or with no
 * experience/education/skill/contact at all, nothing is saved: {@code cv_insufficient_data}, or the
 * model error when the deterministic fallback was in use.
 *
 * <p>Only a payload that passed this check has its skills resolved (D-008): gazetteer matches keep
 * their skill, the others go through {@link SkillResolutionService#resolveSkillMap} (which may create
 * skills/aliases), at most {@code app.cv.max-new-skills} of them taking the embedding + LLM path.
 * A resolver failure keeps only the gazetteer skills.
 */
@Slf4j
@Service
public class CvExtractionServiceImpl implements CvExtractionService {

  static final String DETERMINISTIC_VERSION = "deterministic-v1";
  static final String BRANCH_SCAN_LOW_TEXT = "scan_low_text";
  static final String BRANCH_SCAN_LOW_T = "scan_low_t";
  static final String BRANCH_MODEL_TEXT = "model_text";
  static final String BRANCH_KEYWORD_ONLY = "keyword_only";
  private static final int PARSER_VERSION_MAX_LENGTH = 50;
  private static final double OCR_TEXT_QUALITY = 1.0;

  /** Thresholds and limits read from {@code app.cv.*}. */
  record Settings(
      double tMin,
      double eMin,
      double qHigh,
      double qLow,
      int ocrMinCharsPerPage,
      Duration pdfParseTimeout,
      int pdfParseThreads,
      int maxNewSkills
  ) {
  }

  private final CvPdfReader cvPdfReader;
  private final DeterministicExtractor deterministicExtractor;
  private final GeminiCvExtractionClient geminiClient;
  private final CvPostProcessor postProcessor;
  private final SkillResolutionService skillResolutionService;
  private final CvProfileService cvProfileService;
  private final Settings settings;
  private final ExecutorService pdfExecutor;

  @Autowired
  public CvExtractionServiceImpl(
      CvPdfReader cvPdfReader,
      DeterministicExtractor deterministicExtractor,
      GeminiCvExtractionClient geminiClient,
      CvPostProcessor postProcessor,
      SkillResolutionService skillResolutionService,
      CvProfileService cvProfileService,
      @Value("${app.cv.t-min:0.6}") double tMin,
      @Value("${app.cv.e-min:0.5}") double eMin,
      @Value("${app.cv.q-high:0.8}") double qHigh,
      @Value("${app.cv.q-low:0.45}") double qLow,
      @Value("${app.cv.ocr-min-chars-per-page:100}") int ocrMinCharsPerPage,
      @Value("${app.cv.pdf-parse-timeout:15s}") Duration pdfParseTimeout,
      @Value("${app.cv.pdf-parse-threads:4}") int pdfParseThreads,
      @Value("${app.cv.max-new-skills:10}") int maxNewSkills
  ) {
    this(cvPdfReader, deterministicExtractor, geminiClient, postProcessor, skillResolutionService,
        cvProfileService,
        new Settings(tMin, eMin, qHigh, qLow, ocrMinCharsPerPage, pdfParseTimeout, pdfParseThreads,
            maxNewSkills));
  }

  CvExtractionServiceImpl(
      CvPdfReader cvPdfReader,
      DeterministicExtractor deterministicExtractor,
      GeminiCvExtractionClient geminiClient,
      CvPostProcessor postProcessor,
      SkillResolutionService skillResolutionService,
      CvProfileService cvProfileService,
      Settings settings
  ) {
    this.cvPdfReader = cvPdfReader;
    this.deterministicExtractor = deterministicExtractor;
    this.geminiClient = geminiClient;
    this.postProcessor = postProcessor;
    this.skillResolutionService = skillResolutionService;
    this.cvProfileService = cvProfileService;
    this.settings = settings;
    this.pdfExecutor = Executors.newFixedThreadPool(Math.max(1, settings.pdfParseThreads()), pdfThreadFactory());
  }

  @PreDestroy
  void shutdown() {
    pdfExecutor.shutdownNow();
  }

  @Override
  public CvProfileView extract(UUID userId, MultipartFile file) {
    long start = System.nanoTime();
    Stats stats = new Stats(UUID.randomUUID());

    long readStart = System.nanoTime();
    CvPdfContent content = readPdf(file, stats.uploadId);
    stats.readMs = elapsedMs(readStart);

    Outcome outcome = runPipeline(content, stats);
    if (outcome.failure() != null) {
      logSummary(userId, outcome, stats, "failed", start);
      throw outcome.failure();
    }

    long saveStart = System.nanoTime();
    CvProfileView view = cvProfileService.saveParsed(userId, outcome.payload(), outcome.parserVersion());
    stats.saveMs = elapsedMs(saveStart);

    logSummary(userId, outcome, stats, "saved", start);
    return view;
  }

  private static void logSummary(UUID userId, Outcome outcome, Stats stats, String status, long start) {
    log.info("(extract) uploadId: {}, userId: {}, branch: {}, status: {}, errorCode: {}, fallbackCode: {}, "
            + "detQ: {}, q: {}, usedModel: {}, usedOcr: {}, parserVersion: {}, skillsMatched: {}, "
            + "skillsDropped: {}, skillsMerged: {}, hallucinatedSkills: {}, promptTokens: {}, "
            + "candidateTokens: {}, readMs: {}, detMs: {}, modelMs: {}, postMs: {}, lookupMs: {}, saveMs: {}, "
            + "totalMs: {}",
        stats.uploadId, userId, stats.branch, status,
        outcome.failure() == null ? null : outcome.failure().getCode(), outcome.fallbackCode(),
        round3(stats.detQ), round3(outcome.q()), outcome.usedModel(), outcome.usedOcr(),
        outcome.parserVersion(), stats.skillsMatched, stats.skillsDropped, stats.skillsMerged,
        stats.hallucinated, stats.promptTokens, stats.candidateTokens, stats.readMs, stats.detMs,
        stats.modelMs, stats.postMs, stats.lookupMs, stats.saveMs, elapsedMs(start));
  }

  // ---------------------------------------------------------------- pdf

  private CvPdfContent readPdf(MultipartFile file, UUID uploadId) {
    Future<CvPdfContent> future = pdfExecutor.submit(() -> cvPdfReader.read(file));
    try {
      return future.get(settings.pdfParseTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      log.warn("(readPdf) uploadId: {}, stage: pdf_read, status: timeout, timeoutMs: {}",
          uploadId, settings.pdfParseTimeout().toMillis());
      throw CvExtractionException.unreadable(e);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw CvExtractionException.unreadable(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof CvExtractionException cvException) {
        log.info("(readPdf) uploadId: {}, stage: pdf_read, status: rejected, errorCode: {}",
            uploadId, cvException.getCode());
        throw cvException;
      }
      log.warn("(readPdf) uploadId: {}, stage: pdf_read, status: unreadable, error: {}",
          uploadId, cause == null ? null : cause.getClass().getSimpleName());
      throw CvExtractionException.unreadable(cause);
    }
  }

  // ---------------------------------------------------------------- decision tree (§7.3)

  private Outcome runPipeline(CvPdfContent content, Stats stats) {
    PdfText text = content.text();
    double avgCharsPerPage = text.pageCount() <= 0 ? 0 : (double) text.totalChars() / text.pageCount();
    if (avgCharsPerPage < settings.ocrMinCharsPerPage()) {
      stats.branch = BRANCH_SCAN_LOW_TEXT;
      return scanPath(content, stats);
    }

    long detStart = System.nanoTime();
    DeterministicResult det = deterministicExtractor.extract(text);
    stats.detMs = elapsedMs(detStart);
    stats.detQ = det.q();
    ExtractionMetrics metrics = det.metrics();

    if (metrics.textQuality() < settings.tMin()) {
      stats.branch = BRANCH_SCAN_LOW_T;
      return scanPath(content, stats);
    }
    if (det.layoutComplex() || det.unstructuredExperienceBlocks() > 0
        || metrics.expStructurability() < settings.eMin() || det.q() < settings.qHigh()) {
      stats.branch = BRANCH_MODEL_TEXT;
      return modelTextPath(det, stats);
    }
    stats.branch = BRANCH_KEYWORD_ONLY;
    return finish(null, det, det.preprocessedText(), metrics.textQuality(), false, null, stats);
  }

  /** OCR fallback (D-002): the PDF bytes go to the multimodal model. No deterministic fallback. */
  private Outcome scanPath(CvPdfContent content, Stats stats) {
    if (!geminiClient.isEnabled()) {
      return Outcome.failed(CvExtractionException.unreadable(null), null);
    }
    CvModelResult result;
    try {
      result = callModel(() -> geminiClient.extractFromPdf(content.bytes(), CvExtractionHints.empty()), stats);
    } catch (CvExtractionException e) {
      return Outcome.failed(e, null);
    }
    String crossCheckText = scanCrossCheckText(result.payload(), content.text().fullText());
    return finish(result, null, crossCheckText, OCR_TEXT_QUALITY, true, null, stats);
  }

  private Outcome modelTextPath(DeterministicResult det, Stats stats) {
    if (!geminiClient.isEnabled()) {
      return deterministicFallback(det, CvExtractionException.modelError(null), stats);
    }
    CvModelResult result;
    try {
      result = callModel(() -> geminiClient.extractFromText(det.preprocessedText(), hints(det)), stats);
    } catch (CvExtractionException e) {
      if (e.getKind() == CvExtractionException.ErrorKind.TRANSIENT) {
        return deterministicFallback(det, e, stats);
      }
      return Outcome.failed(e, det.q());
    }
    return finish(result, det, det.preprocessedText(), det.metrics().textQuality(), false, null, stats);
  }

  private Outcome deterministicFallback(DeterministicResult det, CvExtractionException modelError, Stats stats) {
    if (det.q() < settings.qLow()) {
      return Outcome.failed(modelError, det.q());
    }
    return finish(null, det, det.preprocessedText(), det.metrics().textQuality(), false, modelError, stats);
  }

  private CvModelResult callModel(Supplier<CvModelResult> call, Stats stats) {
    long modelStart = System.nanoTime();
    try {
      CvModelResult result = call.get();
      stats.promptTokens = result.promptTokens();
      stats.candidateTokens = result.candidateTokens();
      return result;
    } catch (CvExtractionException e) {
      log.warn("(callModel) uploadId: {}, stage: model, status: failed, errorCode: {}, kind: {}",
          stats.uploadId, e.getCode(), e.getKind());
      throw e;
    } finally {
      stats.modelMs = elapsedMs(modelStart);
    }
  }

  /**
   * Post-process, compute the final quality score, decide success or failure and, on success only,
   * resolve skills.
   *
   * @param modelError model error when the deterministic result is used as a fallback (D-006)
   */
  private Outcome finish(
      CvModelResult model, DeterministicResult det, String crossCheckText, double textQuality,
      boolean usedOcr, CvExtractionException modelError, Stats stats
  ) {
    long postStart = System.nanoTime();
    CvPostProcessResult post = postProcessor.process(
        model == null ? null : model.payload(), det == null ? null : det.toPartialPayload(), crossCheckText);
    stats.postMs = elapsedMs(postStart);
    stats.hallucinated = post.hallucinatedSkillCount();

    CvParsedPayload candidate = post.payload();
    boolean usedModel = model != null;
    String parserVersion = usedModel ? parserVersion(model.model()) : DETERMINISTIC_VERSION;
    double q = finalQuality(candidate, textQuality, det != null && det.layoutComplex());
    String fallbackCode = modelError == null ? null : modelError.getCode();
    if (isThin(candidate) || q < settings.qLow()) {
      CvExtractionException failure = modelError != null ? modelError : CvExtractionException.insufficientData();
      return new Outcome(failure, null, q, usedModel, usedOcr, parserVersion, fallbackCode);
    }

    long lookupStart = System.nanoTime();
    List<SkillItem> skills = resolveSkills(candidate.skills(), stats);
    stats.lookupMs = elapsedMs(lookupStart);
    return new Outcome(null, withSkills(candidate, skills), q, usedModel, usedOcr, parserVersion, fallbackCode);
  }

  // ---------------------------------------------------------------- skills

  /**
   * Keeps skills already resolved by the gazetteer (skillId set), resolves the others by raw name
   * (fallback skillName) through {@link SkillResolutionService#resolveSkillMap} with the
   * {@code max-new-skills} cap, drops those left unresolved and merges duplicates by skillId.
   * A resolver failure is logged and only the gazetteer skills are kept.
   */
  private List<SkillItem> resolveSkills(List<SkillItem> items, Stats stats) {
    List<SkillItem> candidates = nullSafe(items).stream().filter(Objects::nonNull).toList();
    List<String> raws = candidates.stream()
        .filter(item -> item.skillId() == null)
        .map(CvExtractionServiceImpl::rawName)
        .filter(CvExtractionServiceImpl::notBlank)
        .toList();
    Map<String, Skill> resolvedByKey = Map.of();
    if (!raws.isEmpty()) {
      try {
        resolvedByKey = skillResolutionService.resolveSkillMap(raws, settings.maxNewSkills());
      } catch (RuntimeException e) {
        log.warn("(resolveSkills) uploadId: {}, stage: skill_resolve, status: failed, error: {}",
            stats.uploadId, e.getClass().getSimpleName());
      }
    }

    Map<UUID, SkillItem> byId = new LinkedHashMap<>();
    int dropped = 0;
    for (SkillItem item : candidates) {
      SkillRef ref = item.skillId() != null
          ? new SkillRef(item.skillId(), item.skillName())
          : toRef(resolvedByKey, rawName(item));
      if (ref == null) {
        dropped++;
        continue;
      }
      SkillItem resolved = new SkillItem(item.raw(), ref.id(), ref.name(), item.confidence(), item.evidence(),
          item.experienceMonths(), item.lastUsedAt());
      byId.merge(ref.id(), resolved, CvExtractionServiceImpl::mergeSkill);
    }
    stats.skillsMatched = byId.size();
    stats.skillsDropped = dropped;
    stats.skillsMerged = candidates.size() - dropped - byId.size();
    return List.copyOf(byId.values());
  }

  private static String rawName(SkillItem item) {
    return notBlank(item.raw()) ? item.raw() : item.skillName();
  }

  private static SkillRef toRef(Map<String, Skill> resolvedByKey, String raw) {
    if (!notBlank(raw)) {
      return null;
    }
    Skill skill = resolvedByKey.get(SkillUtils.normalizeSkill(raw));
    return skill == null ? null : new SkillRef(skill.getId(), skill.getName());
  }

  private static SkillItem mergeSkill(SkillItem first, SkillItem other) {
    return new SkillItem(
        first.raw(),
        first.skillId(),
        first.skillName() != null ? first.skillName() : other.skillName(),
        maxOrNull(first.confidence(), other.confidence()),
        first.evidence() != null ? first.evidence() : other.evidence(),
        maxOrNull(first.experienceMonths(), other.experienceMonths()),
        maxOrNull(first.lastUsedAt(), other.lastUsedAt()));
  }

  private static <T extends Comparable<T>> T maxOrNull(T a, T b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.compareTo(b) >= 0 ? a : b;
  }

  private static CvParsedPayload withSkills(CvParsedPayload p, List<SkillItem> skills) {
    return new CvParsedPayload(p.contact(), p.currentTitle(), p.summary(), p.experiences(), p.educations(),
        p.projects(), p.certifications(), p.languages(), skills, p.lowConfidenceFields(),
        p.totalMonthsExperience());
  }

  // ---------------------------------------------------------------- quality

  /**
   * Q recomputed on the final payload with the §7.2 weights: T = deterministic text quality
   * (1.0 after OCR by the model), S = present among {experiences, educations, skills, contact},
   * C = {email, phone} present, K = resolved skills / K_TARGET, E = experiences having a start
   * date and a title or company.
   */
  static double finalQuality(CvParsedPayload p, double textQuality, boolean layoutComplex) {
    boolean hasEmail = p.contact() != null && notBlank(p.contact().email());
    boolean hasPhone = p.contact() != null && notBlank(p.contact().phone());
    List<CvParsedPayload.Experience> experiences = nullSafe(p.experiences());
    int sections = (experiences.isEmpty() ? 0 : 1)
        + (nullSafe(p.educations()).isEmpty() ? 0 : 1)
        + (nullSafe(p.skills()).isEmpty() ? 0 : 1)
        + (hasEmail || hasPhone ? 1 : 0);
    int contact = (hasEmail ? 1 : 0) + (hasPhone ? 1 : 0);
    long structured = experiences.stream()
        .filter(e -> e != null && notBlank(e.startDate()) && (notBlank(e.title()) || notBlank(e.company())))
        .count();
    double e = experiences.isEmpty() ? 0 : (double) structured / experiences.size();
    return ExtractionMetrics.of(textQuality, sections / 4.0, contact / 2.0,
        ExtractionMetrics.skillYield(nullSafe(p.skills()).size()), e, layoutComplex).q();
  }

  /** No experience, education, skill, nor email/phone: nothing worth saving. */
  private static boolean isThin(CvParsedPayload p) {
    boolean hasContact = p.contact() != null && (notBlank(p.contact().email()) || notBlank(p.contact().phone()));
    return nullSafe(p.experiences()).isEmpty() && nullSafe(p.educations()).isEmpty()
        && nullSafe(p.skills()).isEmpty() && !hasContact;
  }

  // ---------------------------------------------------------------- model helpers

  private static CvExtractionHints hints(DeterministicResult det) {
    DeterministicResult.ContactInfo contact = det.contact();
    List<String> skills = det.skills().stream().map(match -> match.skill().name()).toList();
    List<String> sections = det.sections().keySet().stream()
        .filter(section -> section != DeterministicResult.Section.HEADER)
        .map(section -> section.name().toLowerCase(Locale.ROOT))
        .toList();
    return new CvExtractionHints(contact.emails(), contact.phones(), contact.links(), skills, sections,
        det.layoutComplex());
  }

  /**
   * In PDF mode there is no reliable native text to cross-check against, so the model output is
   * checked against its own verbatim quotes: descriptions, summary, contact values and skill
   * evidence (never the bare skill lists), plus whatever native text the PDF has. A skill whose
   * evidence does not contain it is still dropped as hallucinated.
   */
  static String scanCrossCheckText(CvParsedPayload p, String nativeText) {
    List<String> parts = new ArrayList<>();
    parts.add(nativeText);
    if (p.contact() != null) {
      parts.add(p.contact().fullName());
      parts.add(p.contact().email());
      parts.add(p.contact().phone());
      parts.add(p.contact().location());
      parts.addAll(nullSafe(p.contact().links()));
    }
    parts.add(p.currentTitle());
    parts.add(p.summary());
    for (CvParsedPayload.Experience e : nullSafe(p.experiences())) {
      if (e != null) {
        parts.add(e.title());
        parts.add(e.company());
        parts.add(e.description());
      }
    }
    for (CvParsedPayload.Project project : nullSafe(p.projects())) {
      if (project != null) {
        parts.add(project.name());
        parts.add(project.description());
      }
    }
    for (SkillItem skill : nullSafe(p.skills())) {
      if (skill != null) {
        parts.add(skill.evidence());
      }
    }
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (notBlank(part)) {
        sb.append(part).append('\n');
      }
    }
    return sb.toString();
  }

  private static String parserVersion(String model) {
    String version = notBlank(model) ? model.trim() : "gemini";
    return version.length() > PARSER_VERSION_MAX_LENGTH ? version.substring(0, PARSER_VERSION_MAX_LENGTH) : version;
  }

  // ---------------------------------------------------------------- misc

  private static ThreadFactory pdfThreadFactory() {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, "cv-pdf-reader-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  private static Double round3(Double value) {
    return value == null ? null : Math.round(value * 1000) / 1000.0;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }

  /**
   * Decision of the pipeline: either {@code failure} (thrown, nothing saved) or {@code payload} (saved).
   * {@code fallbackCode} is the model error code when the deterministic result was used instead (logs only).
   */
  private record Outcome(
      CvExtractionException failure,
      CvParsedPayload payload,
      Double q,
      boolean usedModel,
      boolean usedOcr,
      String parserVersion,
      String fallbackCode
  ) {

    /** Failure before any usable payload: no model output used. */
    static Outcome failed(CvExtractionException failure, Double q) {
      return new Outcome(failure, null, q, false, false, DETERMINISTIC_VERSION, null);
    }
  }

  /** Per-request counters and timings for the summary log line (no CV content). */
  private static final class Stats {

    private final UUID uploadId;
    private String branch;
    private Double detQ;
    private long readMs;
    private long detMs;
    private long modelMs;
    private long postMs;
    private long lookupMs;
    private long saveMs;
    private int skillsMatched;
    private int skillsDropped;
    private int skillsMerged;
    private int hallucinated;
    private Integer promptTokens;
    private Integer candidateTokens;

    private Stats(UUID uploadId) {
      this.uploadId = uploadId;
    }
  }
}
