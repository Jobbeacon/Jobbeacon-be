package com.jobbeacon.repository;

import com.jobbeacon.entity.Category;
import com.jobbeacon.repository.projection.CategoryDistance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends BaseRepository<Category> {
    Optional<Category> findByNameIgnoreCase(String name);
    long countByEmbeddingIsNotNull();

    @Query("select c.id, c.name, c.parent.id, c.level, c.path from Category c")
    List<Object[]> findAllForTree();

    @Query("""
            select new com.jobbeacon.repository.projection.CategoryDistance(c, cosine_distance(c.embedding, :embedding))
            from Category c
            where c.level = :level and c.parent is null and c.embedding is not null
            order by cosine_distance(c.embedding, :embedding) asc
            """)
    List<CategoryDistance> findNearestRootCategories(
            @Param("level") Integer level,
            @Param("embedding") float[] embedding,
            Pageable pageable
    );

    @Query("""
            select new com.jobbeacon.repository.projection.CategoryDistance(c, cosine_distance(c.embedding, :embedding))
            from Category c
            where c.level = :level and c.parent.id = :parentId and c.embedding is not null
            order by cosine_distance(c.embedding, :embedding) asc
            """)
    List<CategoryDistance> findNearestChildCategories(
            @Param("level") Integer level,
            @Param("parentId") UUID parentId,
            @Param("embedding") float[] embedding,
            Pageable pageable
    );
}
