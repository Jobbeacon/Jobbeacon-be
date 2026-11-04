package com.jobbeacon.repository;

import com.jobbeacon.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, String> {

    @Query("select p.nameLocal from Province p")
    List<String> findAllNameLocals();
}
