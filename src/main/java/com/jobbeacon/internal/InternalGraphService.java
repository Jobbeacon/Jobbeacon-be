package com.jobbeacon.internal;

import com.jobbeacon.dto.internal.InternalGraphCandidatesResponse;
import com.jobbeacon.dto.internal.InternalGraphJobResponse;
import com.jobbeacon.repository.GraphSourceRepository;
import com.jobbeacon.repository.projection.GraphJob;
import com.jobbeacon.repository.projection.GraphJobSkill;
import com.jobbeacon.repository.projection.GraphJobSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reads the data the recommendation service builds its graph from. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalGraphService {

    // Postgres orders uuid bytewise, so the nil UUID sorts before every real id and the first page starts there.
    private static final UUID FIRST_ID = new UUID(0, 0);

    private final GraphSourceRepository sourceRepository;

    public List<InternalGraphJobResponse> findJobs(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        List<GraphJob> jobs = sourceRepository.findGraphJobs(jobIds);
        Map<UUID, List<GraphJobSkill>> skillsByJob = sourceRepository.findGraphJobSkills(jobIds).stream()
            .collect(Collectors.groupingBy(GraphJobSkill::jobId));
        Map<UUID, List<GraphJobSource>> sourcesByJob = sourceRepository.findGraphJobSources(jobIds).stream()
            .collect(Collectors.groupingBy(GraphJobSource::jobId));
        return jobs.stream().map(job -> new InternalGraphJobResponse(
            job.id(),
            job.title(),
            job.companyId(),
            job.companyName(),
            job.postedDate(),
            job.expiredDate(),
            skillsByJob.getOrDefault(job.id(), List.of()).stream()
                .map(skill -> new InternalGraphJobResponse.Skill(skill.skillId(), skill.name(), skill.normalizedName()))
                .toList(),
            sourcesByJob.getOrDefault(job.id(), List.of()).stream()
                .map(source -> new InternalGraphJobResponse.Source(source.domain(), source.jobUrl()))
                .toList()
        )).toList();
    }

    public InternalGraphCandidatesResponse findCandidates(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return new InternalGraphCandidatesResponse(List.of(), List.of(), List.of());
        }
        return new InternalGraphCandidatesResponse(
            sourceRepository.findExistingUserIds(userIds),
            sourceRepository.findGraphCandidateSkills(userIds),
            sourceRepository.findGraphCandidateSavedJobs(userIds)
        );
    }

    public List<UUID> findJobIds(UUID after, int limit) {
        return sourceRepository.findJobIdsAfter(startAfter(after), limit);
    }

    public List<UUID> findCandidateIds(UUID after, int limit) {
        return sourceRepository.findCandidateIdsAfter(startAfter(after), limit);
    }

    private static UUID startAfter(UUID after) {
        return after == null ? FIRST_ID : after;
    }
}
