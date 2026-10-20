package com.jobbeacon.entity;

import com.jobbeacon.constant.SalaryCurrency;
import com.jobbeacon.constant.SalaryPeriod;
import com.jobbeacon.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_job_preference")
public class UserJobPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "desired_titles", columnDefinition = "text[]")
    private List<String> desiredTitles;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "employment_types", columnDefinition = "text[]")
    private List<String> employmentTypes;

    @Column(name = "remote_ok", nullable = false)
    private boolean remoteOk = false;

    @Column(name = "expected_salary_min")
    private Long expectedSalaryMin;

    @Column(name = "expected_salary_max")
    private Long expectedSalaryMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false)
    private SalaryCurrency currency = SalaryCurrency.VND;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 20, nullable = false)
    private SalaryPeriod salaryPeriod = SalaryPeriod.MONTH;

    @Column(name = "salary_negotiable", nullable = false)
    private boolean salaryNegotiable = false;

    @Column(name = "open_to_work", nullable = false)
    private boolean openToWork = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_preferred_location",
            joinColumns = @JoinColumn(name = "preference_id"),
            inverseJoinColumns = @JoinColumn(name = "province_id")
    )
    private Set<Province> provinces = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
