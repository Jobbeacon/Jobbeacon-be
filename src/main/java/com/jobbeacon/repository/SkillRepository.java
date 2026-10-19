package com.jobbeacon.repository;

import com.jobbeacon.entity.Skill;
import com.jobbeacon.repository.projection.SkillDistance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends BaseRepository<Skill> {
    Optional<Skill> findByNameIgnoreCase(String name);

    Optional<Skill> findByNormalizedName(String normalizedName);

    @Query("""
            select new com.jobbeacon.repository.projection.SkillDistance(s, cosine_distance(s.embedding, :embedding))
            from Skill s
            where s.embedding is not null and cosine_distance(s.embedding, :embedding) < :maxDistance
            order by cosine_distance(s.embedding, :embedding) asc
            """)
    List<SkillDistance> findNearestSkills(
            @Param("embedding") float[] embedding,
            @Param("maxDistance") double maxDistance,
            Pageable pageable
    );

    /**
     * Names of every skill for the CV gazetteer, without loading embeddings.
     */
    @Query("""
            select s.id as id, s.name as name, s.normalizedName as normalizedName
            from Skill s
            where s.name is not null
            order by s.id asc
            """)
    List<SkillNameRow> findAllNameRowsOrderByIdAsc();

    interface SkillNameRow {
        UUID getId();

        String getName();

        String getNormalizedName();
    }
}
