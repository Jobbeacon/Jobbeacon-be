package com.jobbeacon.repository;

import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.repository.projection.CompanyProvince;
import com.jobbeacon.repository.projection.CompanySource;
import com.jobbeacon.repository.projection.JobProvince;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JobLocationRepository extends BaseRepository<JobLocation> {

    List<JobLocation> findByJobId(UUID jobId);

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

    @Query("select jl from JobLocation jl left join fetch jl.province where jl.job.id = :jobId")
    List<JobLocation> findByJobId(@Param("jobId") UUID jobId);

    @Query(
        """
            select distinct new com.jobbeacon.repository.projection.JobProvince(jl.job.id, jl.province.id)
            from JobLocation jl
            where jl.job.id in :jobIds and jl.province is not null
        """
    )
    List<JobProvince> findProvincesByJobIds(@Param("jobIds") Collection<UUID> jobIds);

    @Modifying
    @Query("delete from JobLocation jl where jl.job.id = :jobId")
    void deleteByJobId(@Param("jobId") UUID jobId);
}
