package com.jobbeacon.dto.cv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Structured result of a CV extraction; partially stored in {@code cv_profile}
 * (experiences/educations as JSONB) and {@code cv_skill_mapping}.
 * Dates are "YYYY-MM" strings or null. Only skills resolved to an existing {@code skill} row are listed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvParsedPayload(
    Contact contact,
    String currentTitle,
    String summary,
    List<Experience> experiences,
    List<Education> educations,
    List<Project> projects,
    List<Certification> certifications,
    List<Language> languages,
    List<SkillItem> skills,
    List<String> lowConfidenceFields,
    Integer totalMonthsExperience
) {

  public static CvParsedPayload empty() {
    return new CvParsedPayload(null, null, null, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), null);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Contact(String fullName, String email, String phone, List<String> links, String location) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Experience(
      String title,
      String company,
      String startDate,
      String endDate,
      boolean current,
      String description,
      List<String> skills
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Education(String school, String degree, String major, String startDate, String endDate) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Project(String name, String role, String description, List<String> skills) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Certification(String name, String issuer, String date) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Language(String name, String level) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SkillItem(
      String raw,
      UUID skillId,
      String skillName,
      Double confidence,
      String evidence,
      Integer experienceMonths,
      String lastUsedAt
  ) {
  }
}
