package com.jobbeacon.repository;

import com.jobbeacon.dto.response.SavedJobResponse;
import com.jobbeacon.entity.UserSavedJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSavedJobRepository extends JpaRepository<UserSavedJob, UserSavedJob.UserSavedJobId> {

  @Modifying
  @Query(
      value = """
          insert into user_saved_job (user_id, job_id)
          values (:userId, :jobId)
          on conflict (user_id, job_id) do nothing
          """,
      nativeQuery = true
  )
  int saveIfAbsent(@Param("userId") UUID userId, @Param("jobId") UUID jobId);

  @Modifying
  @Query("delete from UserSavedJob s where s.id.userId = :userId and s.id.jobId = :jobId")
  int deleteByUserIdAndJobId(@Param("userId") UUID userId, @Param("jobId") UUID jobId);

  @Query("select s.id.jobId from UserSavedJob s where s.id.userId = :userId order by s.createdAt desc")
  List<UUID> findJobIdsByUserId(@Param("userId") UUID userId);

  @Query(
      value = """
          select new com.jobbeacon.dto.response.SavedJobResponse(
              j.id, j.title, c.name, j.minSalary, j.maxSalary, j.location, j.currency, c.logoUrl,
              j.postedDate, j.expiredDate, s.createdAt
          )
          from UserSavedJob s
          join Job j on j.id = s.id.jobId
          left join j.company c
          where s.id.userId = :userId
          order by s.createdAt desc
          """,
      countQuery = "select count(s) from UserSavedJob s where s.id.userId = :userId"
  )
  Page<SavedJobResponse> findSavedJobs(@Param("userId") UUID userId, Pageable pageable);
}
