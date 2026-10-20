package com.jobbeacon.repository;

import com.jobbeacon.entity.Skill;
import com.jobbeacon.entity.SkillAlias;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillAliasRepository extends BaseRepository<SkillAlias> {
    /**
     * Skill targeted by an alias, loaded as an entity so it stays usable outside a transaction.
     */
    @Query("select s from SkillAlias a join a.skill s where a.normalizedAlias = :normalizedAlias")
    Optional<Skill> findSkillByNormalizedAlias(@Param("normalizedAlias") String normalizedAlias);

    /**
     * Aliases with the id and name of their skill for the CV gazetteer, without loading embeddings.
     */
    @Query("""
            select a.normalizedAlias as normalizedAlias, s.id as skillId, s.name as skillName
            from SkillAlias a join a.skill s
            order by s.id asc, a.id asc
            """)
    List<AliasNameRow> findAllNameRowsOrderBySkillIdAsc();

    interface AliasNameRow {
        String getNormalizedAlias();

        UUID getSkillId();

        String getSkillName();
    }
}
