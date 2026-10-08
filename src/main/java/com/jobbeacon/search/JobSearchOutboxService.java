package com.jobbeacon.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.entity.JobSearchOutbox;
import com.jobbeacon.repository.JobSearchOutboxRepository;
import com.jobbeacon.utils.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobSearchOutboxService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JobSearchOutboxRepository jobSearchOutboxRepository;
    private final ObjectMapper objectMapper;

    public void created(Job job, List<JobLocation> jobLocations) {
        append(job.getId(), "c", JobSearchDocuments.from(job, jobLocations));
    }

    public void updated(Job job, List<JobLocation> jobLocations) {
        append(job.getId(), "u", JobSearchDocuments.from(job, jobLocations));
    }

    public void deleted(UUID jobId) {
        append(jobId, "d", null);
    }

    private void append(UUID jobId, String op, JobSearchDocument document) {
        JobSearchOutbox row = new JobSearchOutbox();
        row.setId(UuidUtils.v7());
        row.setJobId(jobId);
        row.setOp(op);
        row.setCreatedAt(Instant.now());
        if (document != null) {
            row.setPayload(objectMapper.convertValue(document, MAP));
        }
        jobSearchOutboxRepository.save(row);
    }
}
