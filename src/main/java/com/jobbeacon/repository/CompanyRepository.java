package com.jobbeacon.repository;

import com.jobbeacon.entity.Company;
import com.jobbeacon.repository.projection.CompanyJobCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends BaseRepository<Company> {
    Optional<Company> findByNameIgnoreCase(String name);

    Optional<Company> findFirstByNormalizedNameOrderByIdAsc(String normalizedName);

    @Query(
            value = """
            select new com.jobbeacon.repository.projection.CompanyJobCount(c.id, c.name, c.logoUrl, count(j.id))
            from Company c
            left join Job j on j.company = c and (j.expiredDate is null or j.expiredDate > CURRENT_TIMESTAMP)
            where :keyword is null or :keyword = '' or LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            group by c.id, c.name, c.logoUrl
            order by count(j.id) desc, c.name asc
        """,
            countQuery = """
            select count(c)
            from Company c
            where :keyword is null or :keyword = '' or LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """
    )
    Page<CompanyJobCount> searchCompanies(@Param("keyword") String keyword, Pageable pageable);
    
    @Query(
            value = """
            select new com.jobbeacon.repository.projection.CompanyJobCount(c.id, c.name, c.logoUrl, count(j.id))
            from Company c
            left join Job j on j.company = c and (j.expiredDate is null or j.expiredDate > CURRENT_TIMESTAMP)
            where c.id = :id
            group by c.id, c.name, c.logoUrl
        """
    )
    Optional<CompanyJobCount> findCompanyJobCountById(@Param("id") java.util.UUID id);
}
