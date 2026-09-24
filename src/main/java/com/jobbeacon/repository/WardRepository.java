package com.jobbeacon.repository;

import com.jobbeacon.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardRepository extends JpaRepository<Ward, String> {
}
