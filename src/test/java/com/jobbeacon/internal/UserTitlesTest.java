package com.jobbeacon.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.dto.cv.CvParsedPayload.Experience;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.UserJobPreference;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserTitlesTest {

  @Test
  void nothingKnownMeansNoTitles() {
    assertThat(UserTitles.of(null, null).isEmpty()).isTrue();
    assertThat(UserTitles.of(new UserJobPreference(), new CvProfile()).isEmpty()).isTrue();
  }

  @Test
  void desiredTitlesWinOverTheCv() {
    UserTitles titles = UserTitles.of(preference("Data Analyst"), cv("Tester"));

    assertThat(titles).isEqualTo(new UserTitles(List.of("Data Analyst"), List.of(), List.of()));
  }

  @Test
  void blankDesiredTitlesDoNotHideTheCv() {
    assertThat(UserTitles.of(preference(" ", "-"), cv("Tester")).current()).containsExactly("Tester");
  }

  @Test
  void levelsAreRemovedFromCvTitles() {
    assertThat(UserTitles.of(null, cv("Senior Backend Developer")).current()).containsExactly("Backend Developer");
    assertThat(UserTitles.of(null, cv("Nhân viên Kế toán")).current()).containsExactly("Kế toán");
    assertThat(UserTitles.of(null, cv("Chuyên viên Tài chính")).current()).containsExactly("Tài chính");
    assertThat(UserTitles.of(null, cv("Internal Auditor")).current()).containsExactly("Internal Auditor");
    assertThat(UserTitles.of(null, cv("Nhân viên")).isEmpty()).isTrue();
  }

  @Test
  void currentJobCountsAsCurrentAndOnlyTheLatestTwoEarlierOnesAsPast() {
    UserTitles titles = UserTitles.of(null, cv(null,
        experience("Oldest", "2015-01", false),
        experience("Tester", "2020-01", false),
        experience("Business Analyst", "2022-05", false),
        experience("Java Developer", null, true),
        experience("Junior Java Developer", "2023-01", false)));

    assertThat(titles.current()).containsExactly("Java Developer");
    assertThat(titles.past()).containsExactly("Business Analyst", "Tester");
  }

  @Test
  void everyWordOfATitleMustBeInTheJobTitle() {
    List<String> titles = List.of("ke toan", "backend developer");

    assertThat(UserTitles.anyMatches(titles, "Nhân viên KẾ TOÁN tổng hợp")).isTrue();
    assertThat(UserTitles.anyMatches(titles, "Senior Backend Java Developer")).isTrue();
    assertThat(UserTitles.anyMatches(titles, "Kỹ sư Backend")).isFalse();
    assertThat(UserTitles.anyMatches(titles, null)).isFalse();
    assertThat(UserTitles.anyMatches(List.of(), "Kế toán")).isFalse();
    assertThat(UserTitles.anyMatches(List.of("C++ Developer"), "C Developer")).isFalse();
  }

  private static UserJobPreference preference(String... titles) {
    UserJobPreference preference = new UserJobPreference();
    preference.setDesiredTitles(List.of(titles));
    return preference;
  }

  private static CvProfile cv(String currentTitle, Experience... experiences) {
    CvProfile cv = new CvProfile();
    cv.setCurrentTitle(currentTitle);
    cv.setExperiences(List.of(experiences));
    return cv;
  }

  private static Experience experience(String title, String endDate, boolean current) {
    return new Experience(title, "Acme", null, endDate, current, null, List.of());
  }
}
