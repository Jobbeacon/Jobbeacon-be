package com.jobbeacon.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.dto.cv.CvParsedPayload.Experience;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.UserJobPreference;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticTextsTest {

  @Test
  void jobTextPutsWhatTellsTheOccupationFirst() {
    String text = SemanticTexts.job("Kế toán tổng hợp", List.of("Tài chính/Kế toán"), List.of("Excel", "MISA"),
        "<p>Lập   báo cáo</p><p>thuế</p>");

    assertThat(text).isEqualTo("""
        Job title: Kế toán tổng hợp
        Category: Tài chính/Kế toán
        Skills: Excel, MISA
        Description: Lập báo cáo thuế""");
  }

  @Test
  void longTextIsCutAtTheEnd() {
    String text = SemanticTexts.job("Tester", List.of(), List.of(), "x".repeat(10_000));

    assertThat(text).hasSize(SemanticTexts.MAX_LENGTH).startsWith("Job title: Tester\nDescription: x");
  }

  @Test
  void candidateTextHasTheDesiredTitlesThenTheLatestJobsOfTheCv() {
    UserJobPreference preference = new UserJobPreference();
    preference.setDesiredTitles(List.of("Business Analyst", " "));
    CvProfile cv = new CvProfile();
    cv.setCurrentTitle("Tester");
    cv.setExperiences(List.of(
        new Experience("Intern", "A", "2018-01", "2018-06", false, null, null),
        new Experience("Tester", "B", "2021-01", null, true, "Viết test case", List.of("Selenium")),
        new Experience("Support", "C", "2019-01", "2020-12", false, null, null),
        new Experience("Sales", "D", "2017-01", "2017-12", false, null, null)));

    assertThat(SemanticTexts.candidate(preference, cv, List.of("SQL"))).isEqualTo("""
        Desired position: Business Analyst
        Current position: Tester
        Experience:
        - Tester: Viết test case (Selenium)
        - Support
        - Intern
        Skills: SQL""");
  }

  @Test
  void candidateWithNothingToGoByHasNoText() {
    assertThat(SemanticTexts.candidate(null, null, List.of())).isEmpty();
    assertThat(SemanticTexts.candidate(new UserJobPreference(), new CvProfile(), List.of())).isEmpty();
  }

  @Test
  void sameTextSameHash() {
    assertThat(SemanticTexts.hash("a")).isEqualTo(SemanticTexts.hash("a")).hasSize(64)
        .isNotEqualTo(SemanticTexts.hash("b"));
  }
}
