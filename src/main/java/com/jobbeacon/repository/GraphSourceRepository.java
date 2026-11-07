package com.jobbeacon.repository;

import com.jobbeacon.entity.Job;
import com.jobbeacon.repository.projection.GraphJob;
import com.jobbeacon.repository.projection.GraphJobSkill;
import com.jobbeacon.repository.projection.GraphJobSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the graph sync: only the columns the graph needs, as projections, so large columns
 * (job description, skill embeddings) are never loaded.
 */
public interface GraphSourceRepository extends BaseRepository<Job> {

    @Query("""
            select new com.jobbeacon.repository.projection.GraphJob(
                j.id, j.title, c.id, c.name, j.postedDate, j.expiredDate)
            from Job j
            left join j.company c
            where j.id in :jobIds
            """)
    List<GraphJob> findGraphJobs(@Param("jobIds") Collection<UUID> jobIds);

    @Query("""
            select new com.jobbeacon.repository.projection.GraphJobSkill(j.id, s.id, s.name, s.normalizedName)
            from Job j
            join j.skills s
            where j.id in :jobIds and s.name is not null and trim(s.name) <> ''
            """)
    List<GraphJobSkill> findGraphJobSkills(@Param("jobIds") Collection<UUID> jobIds);

    @Query("""
            select new com.jobbeacon.repository.projection.GraphJobSource(m.job.id, s.domain, m.jobUrl)
            from JobSourceMapping m
            join m.source s
            where m.job.id in :jobIds
            """)
    List<GraphJobSource> findGraphJobSources(@Param("jobIds") Collection<UUID> jobIds);
}
