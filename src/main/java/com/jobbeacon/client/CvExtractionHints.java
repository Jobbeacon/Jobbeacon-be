package com.jobbeacon.client;

import java.util.List;

/**
 * Signals found by the deterministic pass, passed to the model as untrusted context data
 * (never placed in the system instruction).
 */
public record CvExtractionHints(
    List<String> emails,
    List<String> phones,
    List<String> links,
    List<String> skills,
    List<String> sections,
    boolean layoutComplex
) {

  public CvExtractionHints {
    emails = emails == null ? List.of() : List.copyOf(emails);
    phones = phones == null ? List.of() : List.copyOf(phones);
    links = links == null ? List.of() : List.copyOf(links);
    skills = skills == null ? List.of() : List.copyOf(skills);
    sections = sections == null ? List.of() : List.copyOf(sections);
  }

  public static CvExtractionHints empty() {
    return new CvExtractionHints(null, null, null, null, null, false);
  }

  public boolean isEmpty() {
    return emails.isEmpty() && phones.isEmpty() && links.isEmpty() && skills.isEmpty()
        && sections.isEmpty() && !layoutComplex;
  }
}
