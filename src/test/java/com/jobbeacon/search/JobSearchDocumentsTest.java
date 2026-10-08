package com.jobbeacon.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobbeacon.entity.Company;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.entity.JobSource;
import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.Skill;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchDocumentsTest {

    @Test
    void buildsDenormalizedPayloadWithPlainTextAndProvince() {
        UUID jobId = UUID.fromString("01990000-0000-7000-8000-000000000001");
        UUID companyId = UUID.fromString("01990000-0000-7000-8000-000000000002");
        UUID skillId = UUID.fromString("01990000-0000-7000-8000-000000000003");
        UUID sourceId = UUID.fromString("01990000-0000-7000-8000-000000000004");

        Company company = new Company();
        company.setId(companyId);
        company.setName("Công ty A");
        company.setLogoUrl("https://cdn.example/logo.png");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");

        JobSource source = new JobSource();
        source.setId(sourceId);
        source.setDomain("topcv.vn");

        Job job = new Job();
        job.setId(jobId);
        job.setCompany(company);
        job.setTitle("Backend Java");
        job.setDescription("<p>Làm <b>API</b></p>");
        job.setBenefits("Bảo hiểm");
        job.setSkills(Set.of(skill));
        job.setCategories(Set.of());
        job.setLocation(List.of("Hà Nội"));
        job.setEmploymentType("FULL_TIME");
        job.setExperienceMonths(24);
        job.setMinSalary(20_000_000L);
        job.setMaxSalary(35_000_000L);
        job.setCurrency("VND");
        job.setSalaryPeriod("MONTH");
        job.setPostedDate(Instant.parse("2026-10-01T00:00:00Z"));

        JobSourceMapping mapping = new JobSourceMapping();
        mapping.setSource(source);
        mapping.setJobUrl("https://topcv.vn/job/1");
        job.setJobSourceMappings(Set.of(mapping));

        Province province = new Province();
        province.setId("01");
        province.setNameLocal("Hà Nội");
        JobLocation jobLocation = new JobLocation();
        jobLocation.setProvince(province);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Map<?, ?> payload = mapper.convertValue(JobSearchDocuments.from(job, List.of(jobLocation)), Map.class);

        assertThat(payload.get("title")).isEqualTo("Backend Java");
        assertThat(payload.get("description")).isEqualTo("Làm API");
        assertThat(payload.get("company_name")).isEqualTo("Công ty A");
        assertThat(payload.get("province_ids")).isEqualTo(List.of("01"));
        assertThat(payload.get("province_names")).isEqualTo(List.of("Hà Nội"));
        assertThat(payload.get("posted_date")).isEqualTo("2026-10-01T00:00:00Z");
    }
}
