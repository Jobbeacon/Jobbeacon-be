package com.jobbeacon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cv_skill_mapping")
public class CvSkillMapping {

    @EmbeddedId
    @Builder.Default
    private CvSkillMappingId id = new CvSkillMappingId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("cvProfileId")
    @JoinColumn(name = "cv_profile_id")
    private CvProfile cvProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "experience_months")
    private Integer experienceMonths;

    @Column(name = "last_used_at")
    private LocalDate lastUsedAt;

    @Column(name = "evidence_text", length = Length.LONG32)
    private String evidenceText;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class CvSkillMappingId implements Serializable {
        @Column(name = "cv_profile_id")
        private UUID cvProfileId;

        @Column(name = "skill_id")
        private UUID skillId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CvSkillMappingId that = (CvSkillMappingId) o;
            return Objects.equals(cvProfileId, that.cvProfileId) && Objects.equals(skillId, that.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cvProfileId, skillId);
        }
    }
}
