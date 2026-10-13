package com.jobbeacon.entity;

import com.jobbeacon.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skill")
public class Skill extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "normalized_name", unique = true)
    private String normalizedName;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}
