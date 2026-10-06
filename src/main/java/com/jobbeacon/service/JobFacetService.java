package com.jobbeacon.service;

import com.jobbeacon.dto.response.FacetItemResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobFacetService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<FacetItemResponse> rootCategories() {
        return rows("""
                select c.name, count(m.job_id)
                from category c
                left join job_category_mapping m on m.category_id = c.id
                where c.level = 1
                group by c.name
                order by count(m.job_id) desc, c.name
                """);
    }

    @Transactional(readOnly = true)
    public List<FacetItemResponse> skillsInUse() {
        return rows("""
                select s.name, count(m.job_id)
                from skill s
                join job_skill_mapping m on m.skill_id = s.id
                group by s.name
                order by count(m.job_id) desc, s.name
                """);
    }

    @SuppressWarnings("unchecked")
    private List<FacetItemResponse> rows(String sql) {
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        return rows.stream()
                .map(row -> new FacetItemResponse(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }
}
