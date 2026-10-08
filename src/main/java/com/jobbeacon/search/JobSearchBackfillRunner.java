package com.jobbeacon.search;

import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.repository.JobSearchBackfillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.backfill-on-startup", havingValue = "true")
public class JobSearchBackfillRunner implements ApplicationRunner {

    private final JobSearchBackfillRepository jobSearchBackfillRepository;
    private final JobSearchOutboxService jobSearchOutboxService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<UUID, List<JobLocation>> locationsByJob = new HashMap<>();
        for (JobLocation jobLocation : jobSearchBackfillRepository.findLocationsForSearchBackfill()) {
            locationsByJob.computeIfAbsent(jobLocation.getJob().getId(), ignored -> new ArrayList<>()).add(jobLocation);
        }
        List<Job> jobs = jobSearchBackfillRepository.findAllForSearchBackfill();
        for (Job job : jobs) {
            jobSearchOutboxService.created(job, locationsByJob.getOrDefault(job.getId(), List.of()));
        }
        log.info("Wrote {} job search outbox rows for backfill", jobs.size());
    }
}
