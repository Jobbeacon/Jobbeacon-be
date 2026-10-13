package com.jobbeacon.repository;

import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.repository.projection.CompanySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobSourceMappingRepository extends JpaRepository<JobSourceMapping, JobSourceMapping.JobSourceMappingId> {

    @Query(
        """
            select new com.jobbeacon.repository.projection.CompanySource(j.company.id, s.domain)
            from JobSourceMapping m
            join m.job j
            join m.source s
            where j.company.id in :companyIds
            group by j.company.id, s.domain
        """
    )
    List<CompanySource> getSourcesByCompanyIds(@Param("companyIds") Collection<UUID> companyIds);
}
