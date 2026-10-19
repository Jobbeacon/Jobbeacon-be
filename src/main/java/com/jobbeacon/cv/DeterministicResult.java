package com.jobbeacon.cv;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.dto.skill.SkillRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Output of {@link DeterministicExtractor}. Contains CV content: never log it, only the metrics.
 *
 * @param preprocessedText  normalized text (header/footer removed, whitespace collapsed, lines kept)
 * @param contact           name guess + emails / phones / links found by regex, in order of appearance
 * @param sections          section content (lines joined by {@code \n}); text before the first
 *                          heading is stored under {@link Section#HEADER}
 * @param experienceBlocks  blocks of the experience section split by date-range lines; blocks with
 *                          {@code structured=false} (no title / company found) are kept here but not
 *                          in {@link #toPartialPayload()}, and cap E (see {@link ExtractionMetrics})
 * @param educations        education entries parsed from the EDUCATION section
 * @param skills            skills matched against the skill table (deduplicated by id)
 * @param metrics           T, S, C, K, E, L and Q
 */
public record DeterministicResult(
    String preprocessedText,
    ContactInfo contact,
    Map<Section, String> sections,
    List<ExperienceBlock> experienceBlocks,
    List<CvParsedPayload.Education> educations,
    List<SkillMatch> skills,
    ExtractionMetrics metrics
) {

  /** Without education entries. */
  public DeterministicResult(String preprocessedText, ContactInfo contact, Map<Section, String> sections,
                             List<ExperienceBlock> experienceBlocks, List<SkillMatch> skills,
                             ExtractionMetrics metrics) {
    this(preprocessedText, contact, sections, experienceBlocks, List.of(), skills, metrics);
  }

  /** Confidence given to a skill found by exact gazetteer match. */
  public static final double GAZETTEER_SKILL_CONFIDENCE = 0.9;

  public double q() {
    return metrics.q();
  }

  public boolean layoutComplex() {
    return metrics.layoutComplex();
  }

  /** Dated experience blocks where neither title nor company was found (left out of the payload). */
  public long unstructuredExperienceBlocks() {
    return experienceBlocks.stream().filter(block -> !block.structured()).count();
  }

  /**
   * Builds a partial payload from the deterministic fields only: contact (first email/phone, all
   * links), summary, structured experience blocks, education entries and matched skills. Projects,
   * certifications, languages are left empty for the model / post-processor.
   * Dates are "YYYY-MM" or "YYYY" (month unknown); the post-processor normalizes them.
   */
  public CvParsedPayload toPartialPayload() {
    CvParsedPayload.Contact payloadContact = new CvParsedPayload.Contact(
        contact.fullName(), first(contact.emails()), first(contact.phones()), List.copyOf(contact.links()), null);
    List<CvParsedPayload.Experience> experiences = new ArrayList<>();
    for (ExperienceBlock block : experienceBlocks) {
      if (!block.structured()) {
        continue;
      }
      DateRange range = block.dateRange();
      experiences.add(new CvParsedPayload.Experience(block.title(), block.company(), range.startDate(),
          range.endDate(), range.current(), block.description(), List.copyOf(block.skills())));
    }
    List<CvParsedPayload.SkillItem> skillItems = new ArrayList<>();
    for (SkillMatch match : skills) {
      skillItems.add(new CvParsedPayload.SkillItem(match.matched(), match.skill().id(), match.skill().name(),
          GAZETTEER_SKILL_CONFIDENCE, match.evidence(), null, null));
    }
    String summary = sections.get(Section.SUMMARY);
    return new CvParsedPayload(payloadContact, null, summary == null || summary.isBlank() ? null : summary,
        experiences, List.copyOf(educations), List.of(), List.of(), List.of(), skillItems, List.of(), null);
  }

  private static String first(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  public enum Section {
    HEADER,
    SUMMARY,
    CONTACT,
    EXPERIENCE,
    EDUCATION,
    SKILLS,
    PROJECTS,
    CERTIFICATIONS,
    AWARDS,
    LANGUAGES,
    ACTIVITIES,
    INTERESTS,
    REFERENCES
  }

  /**
   * @param fullName heuristic guess from the first lines of the CV (before any heading), or null
   */
  public record ContactInfo(String fullName, List<String> emails, List<String> phones, List<String> links) {

    public boolean hasEmail() {
      return !emails.isEmpty();
    }

    public boolean hasPhone() {
      return !phones.isEmpty();
    }
  }

  /**
   * A date range found in text.
   *
   * @param startDate "YYYY-MM" or "YYYY" when the month is unknown
   * @param endDate   same format, null when {@code current}
   * @param current   end is "nay / hiện tại / present / now"
   * @param raw       matched text
   */
  public record DateRange(String startDate, String endDate, boolean current, String raw) {
  }

  /**
   * @param lines       all lines of the block (raw, in order)
   * @param dateRange   the date range that anchors the block
   * @param title       detected job title or null
   * @param company     detected company or null
   * @param description block lines except header and date lines, joined by {@code \n}
   * @param skills      names of skills matched inside the block
   * @param structured  has a date range and a title or company
   */
  public record ExperienceBlock(
      List<String> lines,
      DateRange dateRange,
      String title,
      String company,
      String description,
      List<String> skills,
      boolean structured
  ) {
  }

  /**
   * @param skill    skill from the skill table
   * @param matched  text as written in the CV
   * @param evidence the line where it was first found (CV content, never log)
   */
  public record SkillMatch(SkillRef skill, String matched, String evidence) {
  }
}
