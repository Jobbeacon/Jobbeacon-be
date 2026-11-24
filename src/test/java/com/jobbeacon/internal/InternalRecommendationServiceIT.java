package com.jobbeacon.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jobbeacon.constant.SalaryCurrency;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse.CvTitle;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse.PreferenceMatch;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.repository.TitleMatchRepository;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@Testcontainers
@Import({InternalRecommendationService.class, TitleMatchRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InternalRecommendationServiceIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private InternalRecommendationService service;
  @Autowired
  private UserJobPreferenceRepository preferenceRepository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private JdbcTemplate jdbc;

  private final UUID user = UUID.randomUUID();
  private String wantedProvince;
  private String otherProvince;

  @BeforeEach
  void setUp() {
    com.jobbeacon.entity.User entity = new com.jobbeacon.entity.User();
    entity.setId(user);
    entity.setFirebaseUid(user.toString());
    entity.setEmail(user + "@example.com");
    entityManager.merge(entity);
    entityManager.flush();
    List<String> provinces = jdbc.queryForList("select id from province order by id limit 2", String.class);
    wantedProvince = provinces.get(0);
    otherProvince = provinces.get(1);
  }

  @Test
  void withoutAPreferenceThereIsNothingToMatch() {
    UUID job = job("Java Developer", "FULL_TIME", null, null);
    jdbc.update("insert into user_saved_job (user_id, job_id) values (?, ?)", user, job);

    InternalRecommendationContextResponse context = service.context(user, List.of(job));

    assertThat(context.savedJobIds()).containsExactly(job);
    assertThat(context.matches()).isEmpty();
  }

  @Test
  void comparesEachJobWithThePreference() {
    preference(false);
    UUID fits = job("Senior Kế Toán Tổng Hợp", "FULL_TIME", 20_000_000L, 30_000_000L);
    location(fits, wantedProvince);
    UUID misfits = job("Java Developer", "PART_TIME", 8_000_000L, 10_000_000L);
    location(misfits, otherProvince);

    assertThat(service.context(user, List.of(fits, misfits)).matches()).containsExactlyInAnyOrder(
        new PreferenceMatch(fits, true, true, true, true, null, null),
        new PreferenceMatch(misfits, false, false, false, false, null, null));
  }

  @Test
  void whatAJobDoesNotSayIsNotAMismatch() {
    preference(false);
    UUID silent = job("Kế toán", "ONLINE_JOBS", null, null);

    assertThat(service.context(user, List.of(silent)).matches())
        .containsExactly(new PreferenceMatch(silent, false, null, null, null, null, null));
  }

  @Test
  void oneMatchingProvinceIsEnough() {
    preference(false);
    UUID job = job("x", null, null, null);
    location(job, otherProvince);
    location(job, wantedProvince);

    assertThat(service.context(user, List.of(job)).matches().get(0).location()).isTrue();
  }

  @Test
  void aLowerSalaryIsNoMismatchWhenNegotiable() {
    preference(true);
    UUID low = job("x", null, 8_000_000L, 10_000_000L);
    UUID otherCurrency = job("x", null, 300L, 500L);
    jdbc.update("update job set currency = 'USD' where id = ?", otherCurrency);

    assertThat(service.context(user, List.of(low, otherCurrency)).matches())
        .extracting(PreferenceMatch::salary).containsOnlyNulls();
  }

  @Test
  void cvTitlesStandInWhileTheUserHasNoPreference() {
    cv();
    UUID current = job("Java Backend Developer", "FULL_TIME", null, null);
    UUID past = job("Automation Tester", "FULL_TIME", null, null);
    UUID neither = job("Kế toán", "FULL_TIME", null, null);

    assertThat(service.context(user, List.of(current, past, neither)).matches()).containsExactlyInAnyOrder(
        new PreferenceMatch(current, null, null, null, null, CvTitle.CURRENT, null),
        new PreferenceMatch(past, null, null, null, null, CvTitle.PAST, null),
        new PreferenceMatch(neither, null, null, null, null, null, null));
    assertThat(service.titles(user))
        .isEqualTo(new UserTitles(List.of(), List.of("Backend Developer"), List.of("Tester")));
  }

  @Test
  void aDesiredTitleReplacesTheCvTitles() {
    cv();
    preference(false);
    UUID job = job("Java Backend Developer", null, null, null);

    PreferenceMatch match = service.context(user, List.of(job)).matches().get(0);

    assertThat(match.title()).isFalse();
    assertThat(match.cvTitle()).isNull();
  }

  @Test
  void aTitleOfTheSameOccupationMatchesToo() {
    cv();
    UUID sameOccupation = job("Kỹ sư Backend", null, null, null);
    UUID unrelated = job("Kế toán", null, null, null);
    jdbc.update("insert into title_embedding (title_key, title) values ('backend developer', 'x'), ('kỹ sư backend', 'x'), "
        + "('kế toán', 'x')");
    jdbc.update("insert into title_match (user_title_key, job_title_key, matches) values "
        + "('backend developer', 'kỹ sư backend', true), ('backend developer', 'kế toán', false)");

    assertThat(service.context(user, List.of(sameOccupation, unrelated)).matches()).containsExactlyInAnyOrder(
        new PreferenceMatch(sameOccupation, null, null, null, null, CvTitle.CURRENT, null),
        new PreferenceMatch(unrelated, null, null, null, null, null, null));
  }

  @Test
  void experienceIsOnlyComparedWhenBothSidesAreKnown() {
    jdbc.update("insert into cv_profile (id, user_id, total_months_experience) values (?, ?, 24)", UUID.randomUUID(), user);
    UUID more = job("a", null, null, null);
    UUID less = job("b", null, null, null);
    UUID unstated = job("c", null, null, null);
    jdbc.update("update job set experience_months = 60 where id = ?", more);
    jdbc.update("update job set experience_months = 12 where id = ?", less);
    jdbc.update("update job set experience_months = 0 where id = ?", unstated);

    assertThat(service.context(user, List.of(more, less, unstated)).matches())
        .extracting(PreferenceMatch::jobId, PreferenceMatch::missingExperienceMonths)
        .containsExactlyInAnyOrder(tuple(more, 36), tuple(less, 0), tuple(unstated, null));
  }

  private void cv() {
    jdbc.update("insert into cv_profile (id, user_id, current_title, experiences) values (?, ?, ?, ?::jsonb)",
        UUID.randomUUID(), user, "Senior Backend Developer",
        "[{\"title\": \"Junior Tester\", \"endDate\": \"2022-01\", \"current\": false}]");
  }

  private void preference(boolean negotiable) {
    UserJobPreference preference = new UserJobPreference();
    preference.setUserId(user);
    preference.setDesiredTitles(List.of("ke toan tong hop"));
    preference.setEmploymentTypes(List.of("FULL_TIME"));
    preference.setExpectedSalaryMin(15_000_000L);
    preference.setCurrency(SalaryCurrency.VND);
    preference.setSalaryNegotiable(negotiable);
    preference.setProvinces(Set.of(entityManager.find(Province.class, wantedProvince)));
    preferenceRepository.saveAndFlush(preference);
    entityManager.clear();
  }

  private UUID job(String title, String employmentType, Long min, Long max) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into job (id, title, employment_type, min_salary, max_salary, currency, salary_period) "
        + "values (?, ?, ?, ?, ?, 'VND', 'MONTH')", id, title, employmentType, min, max);
    return id;
  }

  private void location(UUID job, String province) {
    jdbc.update("insert into job_location (id, job_id, raw_location, province_id) values (?, ?, 'x', ?)",
        UUID.randomUUID(), job, province);
  }
}
