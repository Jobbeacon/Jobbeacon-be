package com.jobbeacon.repository;

import com.jobbeacon.entity.Category;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends BaseRepository<Category> {
    Optional<Category> findByNameIgnoreCase(String name);
    long countByEmbeddingIsNotNull();
}
