package com.jobbeacon.repository;

import com.jobbeacon.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, String> {

    List<Ward> findByProvinceId(String provinceId);
}
