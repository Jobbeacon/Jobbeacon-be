package com.jobbeacon.repository;

import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.repository.projection.CompanyProvince;
import com.jobbeacon.repository.projection.CompanySource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JobLocationRepository extends BaseRepository<JobLocation> {

    @Query(
        """
            select new com.jobbeacon.repository.projection.CompanyProvince(j.company.id, p.nameLocal)
            from JobLocation jl
            join jl.job j
            join jl.province p
            where j.company.id in :companyIds
            group by j.company.id, p.nameLocal
        """
    )
    List<CompanyProvince> getProvincesNameByCompanyIds(@Param("companyIds") Collection<UUID> companyIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from JobLocation jl where jl.job.id = :jobId")
    int deleteByJobId(@Param("jobId") UUID jobId);
}
