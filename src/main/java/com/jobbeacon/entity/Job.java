package com.jobbeacon.entity;

import com.jobbeacon.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job")
public class Job extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "experience_months")
    private Integer experienceMonths;

    @Column(name = "employment_type", length = 100)
    private String employmentType;

    @Column(name = "posted_date")
    private Instant postedDate;

    @Column(name = "expired_date")
    private Instant expiredDate;

    @Column(name = "min_salary")
    private Long minSalary;

    @Column(name = "max_salary")
    private Long maxSalary;

    @Column(name = "currency", length = 100)
    private String currency;

    @Column(name = "salary_period", length = 100)
    private String salaryPeriod;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_skill_mapping",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_category_mapping",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<JobSourceMapping> jobSourceMappings = new HashSet<>();
}
