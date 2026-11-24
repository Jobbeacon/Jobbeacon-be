package com.jobbeacon.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.jobbeacon.client.GeminiRetryExecutor.GeminiDeadlineExceededException;
import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.exception.CvExtractionException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Structured CV extraction with Gemini (D-001, D-002). Returns raw model output; cross-check against the
 * source text is the caller's job (§7.4). Never logs CV content.
 */
@Slf4j
@Component
public class GeminiCvExtractionClient {

  static final String MODE_TEXT = "text";
  static final String MODE_PDF = "pdf";
  private static final String PDF_MIME_TYPE = "application/pdf";
  private static final String JSON_MIME_TYPE = "application/json";
  private static final Duration MIN_REQUEST_TIMEOUT = Duration.ofSeconds(10);
  /** The user is waiting: every model and key once, no second round. */
  private static final GeminiRetryPolicy NO_RETRY = new GeminiRetryPolicy(0, 0, 1);

  private static final ObjectMapper MAPPER = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  private final GeminiRetryExecutor retryExecutor;
  private final boolean enabled;
  /** Best first. */
  private final List<String> models;
  private final Duration timeout;
  private final Duration deadline;
  private final int maxOutputTokens;
  private final int thinkingBudget;

  public GeminiCvExtractionClient(
      GeminiRetryExecutor retryExecutor,
      GeminiClientPool clientPool,
      @Value("${app.cv.models:${app.gemini.chat-models}}") List<String> models,
      @Value("${app.cv.model-timeout:30s}") Duration timeout,
      @Value("${app.cv.model-deadline:50s}") Duration deadline,
      @Value("${app.cv.max-output-tokens:8192}") int maxOutputTokens,
      @Value("${app.cv.model-thinking-budget:0}") int thinkingBudget
  ) {
    this.retryExecutor = retryExecutor;
    this.enabled = clientPool.isConfigured();
    if (!enabled) {
      log.warn("(init) Gemini API key is not configured (app.gemini.api-key). CV model extraction is disabled.");
    }
    this.models = models;
    this.timeout = timeout;
    this.deadline = deadline;
    this.maxOutputTokens = maxOutputTokens;
    this.thinkingBudget = thinkingBudget;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Text mode: sends the preprocessed CV text (between delimiters) plus deterministic hints. */
  public CvModelResult extractFromText(String preprocessedText, CvExtractionHints hints) {
    if (preprocessedText == null || preprocessedText.isBlank()) {
      throw CvExtractionException.insufficientData();
    }
    Content content = Content.builder()
        .role("user")
        .parts(Part.fromText(CvExtractionPrompt.buildTextPrompt(preprocessedText, hints)))
        .build();
    return generate(MODE_TEXT, content);
  }

  /** PDF mode (OCR fallback, D-002): sends the PDF bytes to the multimodal model. */
  public CvModelResult extractFromPdf(byte[] pdfBytes, CvExtractionHints hints) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      throw CvExtractionException.invalidFileType();
    }
    Content content = Content.builder()
        .role("user")
        .parts(
            Part.fromText(CvExtractionPrompt.buildPdfPrompt(hints)),
            Part.fromBytes(pdfBytes, PDF_MIME_TYPE))
        .build();
    return generate(MODE_PDF, content);
  }

  GenerateContentConfig buildConfig(String model, Instant deadlineAt) {
    GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(CvExtractionPrompt.SYSTEM_INSTRUCTION)))
        .responseMimeType(JSON_MIME_TYPE)
        .responseSchema(CvExtractionPrompt.responseSchema())
        .temperature(0f)
        .candidateCount(1)
        .maxOutputTokens(maxOutputTokens)
        .httpOptions(HttpOptions.builder()
            .timeout(requestTimeoutMs(deadlineAt))
            .retryOptions(HttpRetryOptions.builder().attempts(1).build())
            .build());
    GeminiThinking.forModel(model, null, thinkingBudget).ifPresent(builder::thinkingConfig);
    return builder.build();
  }

  /** What is left until the deadline, but not below the shortest timeout Gemini accepts. */
  private int requestTimeoutMs(Instant deadlineAt) {
    long remainingMs = Duration.between(Instant.now(), deadlineAt).toMillis();
    return Math.toIntExact(Math.min(timeout.toMillis(), Math.max(MIN_REQUEST_TIMEOUT.toMillis(), remainingMs)));
  }

  private CvModelResult generate(String mode, Content content) {
    if (!enabled) {
      log.warn("(generate) mode: {}, status: disabled, reason: Gemini API key is not configured", mode);
      throw CvExtractionException.modelError(null);
    }

    long start = System.nanoTime();
    GenerateContentResponse response;
    try {
      response = callModel(content);
    } catch (CvExtractionException e) {
      log.warn("(generate) mode: {}, models: {}, status: {}, latencyMs: {}, cause: {}",
          mode, models, e.getCode(), elapsedMs(start), causeName(e.getCause()));
      throw e;
    }
    long latencyMs = elapsedMs(start);

    Optional<GenerateContentResponseUsageMetadata> usage = response.usageMetadata();
    Integer promptTokens = usage.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(null);
    Integer candidateTokens =
        usage.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(null);
    Integer thoughtsTokens = usage.flatMap(GenerateContentResponseUsageMetadata::thoughtsTokenCount).orElse(null);
    String usedModel = response.modelVersion().orElse(models.get(0));

    CvParsedPayload payload;
    try {
      payload = parsePayload(response.text());
    } catch (CvExtractionException e) {
      log.warn("(generate) mode: {}, model: {}, status: invalid_response, finishReason: {}, promptTokens: {}, "
              + "candidateTokens: {}, latencyMs: {}",
          mode, usedModel, finishReason(response), promptTokens, candidateTokens, latencyMs);
      throw e;
    }

    log.info("(generate) mode: {}, model: {}, status: ok, promptTokens: {}, candidateTokens: {}, "
            + "thoughtsTokens: {}, latencyMs: {}",
        mode, usedModel, promptTokens, candidateTokens, thoughtsTokens, latencyMs);
    return new CvModelResult(payload, usedModel, promptTokens, candidateTokens, thoughtsTokens, latencyMs);
  }

  private GenerateContentResponse callModel(Content content) {
    Instant deadlineAt = Instant.now().plus(deadline);
    try {
      return retryExecutor.execute("extractCv", models, NO_RETRY, deadlineAt,
          (client, model) -> client.models.generateContent(
              model, List.of(content), buildConfig(model, deadlineAt)));
    } catch (GeminiDeadlineExceededException e) {
      throw CvExtractionException.modelTimeout(e);
    } catch (RuntimeException e) {
      throw isTimeout(e) ? CvExtractionException.modelTimeout(e) : CvExtractionException.modelError(e);
    }
  }

  /** Maps the model JSON to the payload; server-side fields (skillId, lowConfidenceFields, …) are dropped. */
  static CvParsedPayload parsePayload(String json) {
    if (json == null || json.isBlank()) {
      throw CvExtractionException.modelError(null);
    }
    CvParsedPayload raw;
    try {
      raw = MAPPER.readValue(stripCodeFence(json), CvParsedPayload.class);
    } catch (JsonProcessingException e) {
      throw CvExtractionException.modelError(e);
    }
    if (raw == null) {
      throw CvExtractionException.modelError(null);
    }
    List<CvParsedPayload.SkillItem> skills = orEmpty(raw.skills()).stream()
        .filter(s -> s != null && s.raw() != null && !s.raw().isBlank())
        .map(s -> new CvParsedPayload.SkillItem(
            s.raw().strip(), null, null, s.confidence(), s.evidence(), s.experienceMonths(), s.lastUsedAt()))
        .toList();
    return new CvParsedPayload(
        raw.contact(),
        raw.currentTitle(),
        raw.summary(),
        nonNull(raw.experiences()),
        nonNull(raw.educations()),
        nonNull(raw.projects()),
        nonNull(raw.certifications()),
        nonNull(raw.languages()),
        skills,
        List.of(),
        null
    );
  }

  private static String stripCodeFence(String json) {
    String trimmed = json.strip();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastFence > firstNewline) {
        return trimmed.substring(firstNewline + 1, lastFence).strip();
      }
    }
    return trimmed;
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static <T> List<T> nonNull(List<T> list) {
    return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
  }

  private static boolean isTimeout(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof TimeoutException || c instanceof InterruptedIOException) {
        return true;
      }
      if (c.getCause() == c) {
        break;
      }
    }
    return false;
  }

  private static String causeName(Throwable t) {
    if (t == null) {
      return null;
    }
    if (t instanceof ApiException api) {
      return t.getClass().getSimpleName() + "(" + api.code() + ")";
    }
    return t.getClass().getSimpleName();
  }

  private static String finishReason(GenerateContentResponse response) {
    try {
      return String.valueOf(response.finishReason());
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }
}
