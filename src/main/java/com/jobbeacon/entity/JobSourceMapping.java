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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_source_mapping")
public class JobSourceMapping {

    @EmbeddedId
    @Builder.Default
    private JobSourceMappingId id = new JobSourceMappingId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jobId")
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sourceId")
    @JoinColumn(name = "source_id")
    private JobSource source;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    @Column(name = "crawled_at")
    private Instant crawledAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class JobSourceMappingId implements Serializable {
        @Column(name = "job_id")
        private UUID jobId;

        @Column(name = "source_id")
        private UUID sourceId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JobSourceMappingId that = (JobSourceMappingId) o;
            return Objects.equals(jobId, that.jobId) && Objects.equals(sourceId, that.sourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobId, sourceId);
        }
    }
}
