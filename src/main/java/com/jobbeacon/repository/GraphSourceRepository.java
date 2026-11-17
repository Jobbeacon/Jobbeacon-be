package com.jobbeacon.repository;

import com.jobbeacon.entity.Job;
import com.jobbeacon.repository.projection.GraphCandidateSavedJob;
import com.jobbeacon.repository.projection.GraphCandidateSkill;
import com.jobbeacon.repository.projection.GraphJob;
import com.jobbeacon.repository.projection.GraphJobSkill;
import com.jobbeacon.repository.projection.GraphJobSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

    @Query("select u.id from User u where u.id in :userIds")
    List<UUID> findExistingUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("""
            select new com.jobbeacon.repository.projection.GraphCandidateSkill(
                p.userId, s.id, s.name, s.normalizedName, m.confidence, m.experienceMonths)
            from CvSkillMapping m
            join m.cvProfile p
            join m.skill s
            where p.userId in :userIds and s.name is not null and trim(s.name) <> ''
            """)
    List<GraphCandidateSkill> findGraphCandidateSkills(@Param("userIds") Collection<UUID> userIds);

    @Query("""
            select new com.jobbeacon.repository.projection.GraphCandidateSavedJob(
                s.id.userId, s.id.jobId, s.createdAt)
            from UserSavedJob s
            where s.id.userId in :userIds
            """)
    List<GraphCandidateSavedJob> findGraphCandidateSavedJobs(@Param("userIds") Collection<UUID> userIds);

    @Query(value = """
            select id from job
            where id > :after
            order by id
            limit :limit
            """, nativeQuery = true)
    List<UUID> findJobIdsAfter(@Param("after") UUID after, @Param("limit") int limit);

    @Query(value = """
            select user_id from (
                select user_id from cv_profile
                union
                select user_id from user_saved_job
            ) candidates
            where user_id > :after
            order by user_id
            limit :limit
            """, nativeQuery = true)
    List<UUID> findCandidateIdsAfter(@Param("after") UUID after, @Param("limit") int limit);
}
