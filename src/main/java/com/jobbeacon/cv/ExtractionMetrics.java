package com.jobbeacon.cv;

/**
 * Quality signals of the deterministic pass (analysis doc §7.2). Every score is in [0, 1].
 *
 * @param textQuality         T: {@code 1 - garbage_chars / total_chars} (non-whitespace chars)
 * @param sectionCoverage     S: found / expected sections (experience, education, skills, contact)
 * @param contactCompleteness C: found / expected contact fields (email, phone)
 * @param skillYield          K: {@code min(1, skills_matched / K_TARGET)}
 * @param expStructurability  E: structured experience blocks / all experience blocks (0 when none),
 *                            capped at {@link #E_INCOMPLETE_CAP} when any block is unstructured
 * @param layoutComplex       L: multi-column / interleaved / bilingual layout detected
 * @param q                   weighted score {@code 0.15T + 0.25S + 0.10C + 0.20K + 0.30E}
 */
public record ExtractionMetrics(
    double textQuality,
    double sectionCoverage,
    double contactCompleteness,
    double skillYield,
    double expStructurability,
    boolean layoutComplex,
    double q
) {

  public static final double W_TEXT_QUALITY = 0.15;
  public static final double W_SECTION_COVERAGE = 0.25;
  public static final double W_CONTACT_COMPLETENESS = 0.10;
  public static final double W_SKILL_YIELD = 0.20;
  public static final double W_EXP_STRUCTURABILITY = 0.30;
  public static final int K_TARGET = 5;
  /**
   * Upper bound of E when at least one dated experience block has neither title nor company. Such a
   * block is left out of the deterministic payload, so the keyword-only branch would lose it; the
   * cap keeps E below the default {@code app.cv.e-min} (0.5) and sends the CV to the model.
   */
  public static final double E_INCOMPLETE_CAP = 0.25;

  public static ExtractionMetrics of(double t, double s, double c, double k, double e, boolean layoutComplex) {
    double q = W_TEXT_QUALITY * t
        + W_SECTION_COVERAGE * s
        + W_CONTACT_COMPLETENESS * c
        + W_SKILL_YIELD * k
        + W_EXP_STRUCTURABILITY * e;
    return new ExtractionMetrics(t, s, c, k, e, layoutComplex, clamp(q));
  }

  public static double skillYield(int skillsMatched) {
    return Math.min(1.0, skillsMatched / (double) K_TARGET);
  }

  /** E from block counts: 0 without blocks, the ratio when all are structured, else capped. */
  public static double expStructurability(long structuredBlocks, long totalBlocks) {
    if (totalBlocks <= 0) {
      return 0.0;
    }
    double ratio = structuredBlocks / (double) totalBlocks;
    return structuredBlocks < totalBlocks ? Math.min(ratio, E_INCOMPLETE_CAP) : ratio;
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
