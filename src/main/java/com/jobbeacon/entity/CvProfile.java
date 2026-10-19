package com.jobbeacon.entity;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "cv_profile")
public class CvProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "current_title")
    private String currentTitle;

    @Column(name = "summary", length = Length.LONG32)
    private String summary;

    @Column(name = "total_months_experience")
    private Integer totalMonthsExperience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experiences", columnDefinition = "jsonb")
    private List<CvParsedPayload.Experience> experiences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "educations", columnDefinition = "jsonb")
    private List<CvParsedPayload.Education> educations;

    @Column(name = "parser_model_version", length = 50)
    private String parserModelVersion;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
