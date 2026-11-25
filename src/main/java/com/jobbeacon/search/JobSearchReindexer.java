package com.jobbeacon.search;

import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.repository.JobSearchBackfillRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appends an outbox "updated" row for every given job after an admin change that alters what the
 * search document holds (company name, skill or category names, ...). Flushes and clears the
 * persistence context first, so callers must reload entities they still need afterwards.
 */
@Service
@RequiredArgsConstructor
public class JobSearchReindexer {

    private static final int CHUNK = 500;

    private final JobSearchBackfillRepository jobSearchBackfillRepository;
    private final JobSearchOutboxService jobSearchOutboxService;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void reindex(Collection<UUID> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        entityManager.flush();
        entityManager.clear();
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(jobIds));
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<UUID> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            Map<UUID, List<JobLocation>> locations = new HashMap<>();
            for (JobLocation location : jobSearchBackfillRepository.findLocationsForSearchByJobIds(chunk)) {
                locations.computeIfAbsent(location.getJob().getId(), id -> new ArrayList<>()).add(location);
            }
            for (Job job : jobSearchBackfillRepository.findForSearchByIds(chunk)) {
                jobSearchOutboxService.updated(job, locations.getOrDefault(job.getId(), List.of()));
            }
            entityManager.flush();
            entityManager.clear();
        }
    }
}
