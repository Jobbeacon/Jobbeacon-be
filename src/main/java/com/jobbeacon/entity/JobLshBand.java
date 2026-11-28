package com.jobbeacon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * One LSH band bucket of a job's MinHash signature. Composite primary key (job_id, band_idx), so this entity does not
 * extend {@code BaseEntity}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_lsh_band")
public class JobLshBand {

    @EmbeddedId
    private JobLshBandId id;

    @Column(name = "band_hash", nullable = false)
    private long bandHash;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class JobLshBandId implements Serializable {
        @Column(name = "job_id", nullable = false)
        private UUID jobId;

        @Column(name = "band_idx", nullable = false)
        private short bandIdx;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JobLshBandId that = (JobLshBandId) o;
            return bandIdx == that.bandIdx && Objects.equals(jobId, that.jobId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobId, bandIdx);
        }
    }
}
