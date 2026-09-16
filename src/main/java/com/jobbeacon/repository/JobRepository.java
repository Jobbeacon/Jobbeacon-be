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
            select new com.jobbeacon.dto.response.JobSummaryResponse(
                j.id, j.title, c.name, j.minSalary, j.maxSalary, j.location, j.currency
            )
            from Job j
            left join j.company c
            where :keyword is null or :keyword = ''  or LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            order by j.postedDate desc
        """
    )
    Page<JobSummaryResponse> searchJobs(@Param("keyword") String keyword, Pageable pageable);
}
