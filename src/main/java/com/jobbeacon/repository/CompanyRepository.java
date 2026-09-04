package com.jobbeacon.repository;

import com.jobbeacon.entity.Company;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends BaseRepository<Company> {
    Optional<Company> findByNameIgnoreCase(String name);
}
