package com.jobbeacon.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceJobKeysTest {

  @Test
  void vietnamWorksUrlsOfOnePostingShareItsId() {
    assertThat(SourceJobKeys.from("https://www.vietnamworks.com/accounting-lead-2126092-jv")).isEqualTo("2126092");
    assertThat(SourceJobKeys.from("https://www.vietnamworks.com/accounting-lead-2126092-jd/?source=search#top"))
        .isEqualTo("2126092");
    assertThat(SourceJobKeys.from("https://www.vietnamworks.com/a-1794994592074031452-2132820-jd")).isEqualTo("2132820");
  }

  @Test
  void otherUrlsAreIdentifiedByTheirPath() {
    assertThat(SourceJobKeys.from("https://itviec.com/it-jobs/java-dev-acme-1234?lab=x")).isEqualTo("/it-jobs/java-dev-acme-1234");
    assertThat(SourceJobKeys.from("http://WWW.topcv.vn/viec-lam/ke-toan/99.html/")).isEqualTo("/viec-lam/ke-toan/99.html");
    assertThat(SourceJobKeys.from("https://itviec.com/it-jobs/acme-2126092-jv")).isEqualTo("/it-jobs/acme-2126092-jv");
    assertThat(SourceJobKeys.from("https://www.vietnamworks.com/some-page")).isEqualTo("/some-page");
  }

  @Test
  void urlsWithoutAPathHaveNoKey() {
    assertThat(SourceJobKeys.from("https://itviec.com/?id=1")).isNull();
    assertThat(SourceJobKeys.from("not a url")).isNull();
    assertThat(SourceJobKeys.from(null)).isNull();
  }
}
