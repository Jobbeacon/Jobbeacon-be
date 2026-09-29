package com.jobbeacon.repository;

import com.jobbeacon.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, String> {
}
