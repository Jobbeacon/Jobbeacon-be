package com.jobbeacon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobbeacon.configuration.MarketProperties;
import com.jobbeacon.dto.response.market.MarketOverviewResponse;
import com.jobbeacon.dto.response.market.MarketOverviewResponse.Group;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.repository.MarketElasticsearchRepository;
import com.jobbeacon.repository.MarketElasticsearchRepository.Filter;
import com.jobbeacon.repository.MarketElasticsearchRepository.Overview;
import com.jobbeacon.repository.MarketElasticsearchRepository.Salary;
import com.jobbeacon.repository.ProvinceRepository;
import com.jobbeacon.repository.SkillRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketInsightServiceTest {

  private static final String IT = UUID.randomUUID().toString();
  private static final String BACKEND = UUID.randomUUID().toString();
  private static final String JAVA_DEV = UUID.randomUUID().toString();
  private static final String SALES = UUID.randomUUID().toString();
  private static final UUID JAVA = UUID.randomUUID();

  private final MarketElasticsearchRepository marketRepository = mock(MarketElasticsearchRepository.class);
  private final SkillRepository skillRepository = mock(SkillRepository.class);
  private final ProvinceRepository provinceRepository = mock(ProvinceRepository.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final MarketInsightService service = new MarketInsightService(
      marketRepository, skillRepository, provinceRepository, entityManager, new MarketProperties(25_000, 5));

  @BeforeEach
  void categoryTree() {
    Query query = mock(Query.class);
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {IT, null, "Công nghệ thông tin"});
    rows.add(new Object[] {BACKEND, IT, "Backend"});
    rows.add(new Object[] {JAVA_DEV, BACKEND, "Java Developer"});
    rows.add(new Object[] {SALES, null, "Kinh doanh"});
    when(query.getResultList()).thenReturn(rows);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    Skill java = new Skill();
    java.setId(JAVA);
    java.setName("Java");
    when(skillRepository.findAllById(any())).thenReturn(List.of(java));
    Province hanoi = new Province();
    hanoi.setId("01");
    hanoi.setNameLocal("Hà Nội");
    when(provinceRepository.findAllById(any())).thenReturn(List.of(hanoi));
  }

  @Test
  void smallGroupsKeepTheirCountsButNoSalaryFigures() {
    givenOverview(new Overview(
        40,
        new Salary(10L, 20L, 30L, 12),
        List.of(new MarketElasticsearchRepository.Group(JAVA.toString(), 10, new Salary(15L, 25L, 35L, 4))),
        List.of(new MarketElasticsearchRepository.Group("01", 20, new Salary(11L, 21L, 31L, 5))),
        List.of(),
        List.of(new MarketElasticsearchRepository.Group("y1_3", 8, new Salary(null, null, null, 0))),
        List.of()));

    MarketOverviewResponse response = service.overview(null, null, null);

    assertThat(response.jobsWithSalary()).isEqualTo(12);
    assertThat(response.salary().median()).isEqualTo(20L);
    Group java = response.topSkills().get(0);
    assertThat(java.name()).isEqualTo("Java");
    assertThat(java.share()).isEqualTo(0.25);
    assertThat(java.salary().median()).isNull();
    assertThat(java.salary().sampleSize()).isEqualTo(4);
    assertThat(response.byProvince().get(0).name()).isEqualTo("Hà Nội");
    assertThat(response.byProvince().get(0).salary().median()).isEqualTo(21L);
    assertThat(response.byExperience().get(0).name()).isEqualTo("1–3 năm");
  }

  @Test
  void categoriesAreGroupedByRootCoveringTheSubtreeAndEmptyRootsAreLeftOut() {
    givenOverview(new Overview(10, new Salary(null, null, null, 0), List.of(), List.of(),
        List.of(
            new MarketElasticsearchRepository.Group(SALES, 0, new Salary(null, null, null, 0)),
            new MarketElasticsearchRepository.Group(IT, 6, new Salary(null, null, null, 0))),
        List.of(), List.of()));

    MarketOverviewResponse response = service.overview(null, null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, List<String>>> groups = ArgumentCaptor.forClass(Map.class);
    verify(marketRepository).overview(any(), groups.capture(), eq(25_000.0));
    assertThat(groups.getValue().get(IT)).containsExactlyInAnyOrder(IT, BACKEND, JAVA_DEV);
    assertThat(groups.getValue().get(SALES)).containsExactly(SALES);
    assertThat(response.byCategory()).extracting(Group::name, Group::jobCount)
        .containsExactly(tuple("Công nghệ thông tin", 6L));
  }

  @Test
  void aCategoryFilterCoversItsSubtreeAndAnUnknownOneMatchesNothing() {
    givenOverview(new Overview(0, new Salary(null, null, null, 0), List.of(), List.of(), List.of(), List.of(), List.of()));
    ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);

    service.overview(UUID.fromString(BACKEND), " 01 ", JAVA);
    service.overview(UUID.randomUUID(), "", null);

    verify(marketRepository, times(2))
        .overview(filter.capture(), anyMap(), eq(25_000.0));
    Filter backend = filter.getAllValues().get(0);
    assertThat(backend.categoryIds()).containsExactlyInAnyOrder(BACKEND, JAVA_DEV);
    assertThat(backend.provinceId()).isEqualTo("01");
    assertThat(backend.skillId()).isEqualTo(JAVA.toString());
    Filter unknown = filter.getAllValues().get(1);
    assertThat(unknown.categoryIds()).isEmpty();
    assertThat(unknown.provinceId()).isNull();
  }

  private void givenOverview(Overview overview) {
    when(marketRepository.overview(any(), anyMap(), eq(25_000.0))).thenReturn(overview);
  }
}
