package com.jobbeacon.repository;

import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends BaseRepository<Job> {

    @Query(
            """
                    select j.id as id, j.title as title, c.name as companyName, j.minSalary as minSalary, j.maxSalary as maxSalary, j.location as location, j.currency as currency
                    from Job j
                    left join Company c
                    where LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    order by j.postedDate desc
            """
    )
    Page<JobSummaryResponse> searchJobs(@Param("keyword") String keyword, Pageable pageable);
}
