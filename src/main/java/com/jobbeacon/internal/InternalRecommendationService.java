package com.jobbeacon.internal;

import com.jobbeacon.constant.EmploymentType;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse.PreferenceMatch;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import com.jobbeacon.repository.UserSavedJobRepository;
import com.jobbeacon.repository.projection.JobPreferenceFacts;
import com.jobbeacon.repository.projection.JobProvince;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Compares jobs with what a user asked for, on behalf of the recommendation service. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalRecommendationService {

    private static final Set<String> KNOWN_EMPLOYMENT_TYPES =
        Arrays.stream(EmploymentType.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final UserSavedJobRepository userSavedJobRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final JobRepository jobRepository;
    private final JobLocationRepository jobLocationRepository;

    public InternalRecommendationContextResponse context(UUID userId, Collection<UUID> jobIds) {
        List<UUID> savedJobIds = userSavedJobRepository.findJobIdsByUserId(userId);
        List<PreferenceMatch> matches = jobIds.isEmpty()
            ? List.of()
            : userJobPreferenceRepository.findByUserId(userId)
                .map(preference -> matches(preference, jobIds))
                .orElse(List.of());
        return new InternalRecommendationContextResponse(savedJobIds, matches);
    }

    private List<PreferenceMatch> matches(UserJobPreference preference, Collection<UUID> jobIds) {
        Set<String> wantedProvinces = preference.getProvinces().stream().map(Province::getId).collect(Collectors.toSet());
        Map<UUID, Set<String>> provincesByJob = wantedProvinces.isEmpty()
            ? Map.of()
            : jobLocationRepository.findProvincesByJobIds(jobIds).stream().collect(Collectors.groupingBy(
                JobProvince::jobId, Collectors.mapping(JobProvince::provinceId, Collectors.toSet())));
        List<Set<String>> wantedTitles = preference.getDesiredTitles() == null
            ? List.of()
            : preference.getDesiredTitles().stream().map(InternalRecommendationService::words)
                .filter(words -> !words.isEmpty()).toList();
        return jobRepository.findPreferenceFactsByIds(jobIds).stream()
            .map(job -> new PreferenceMatch(
                job.jobId(),
                titleMatches(wantedTitles, job.title()),
                locationMatches(wantedProvinces, provincesByJob.getOrDefault(job.jobId(), Set.of())),
                employmentTypeMatches(preference.getEmploymentTypes(), job.employmentType()),
                salaryMatches(preference, job)))
            .toList();
    }

    /** True when every word of one wanted title is in the job's title, ignoring case, accents and order. */
    private static Boolean titleMatches(List<Set<String>> wantedTitles, String title) {
        if (wantedTitles.isEmpty() || title == null) {
            return null;
        }
        Set<String> words = words(title);
        return wantedTitles.stream().anyMatch(words::containsAll);
    }

    private static Boolean locationMatches(Set<String> wanted, Set<String> provinces) {
        if (wanted.isEmpty() || provinces.isEmpty()) {
            return null;
        }
        return !Collections.disjoint(wanted, provinces);
    }

    /** Sources also use types users cannot choose (e.g. ONLINE_JOBS), which say nothing about what they want. */
    private static Boolean employmentTypeMatches(List<String> wanted, String employmentType) {
        if (wanted == null || wanted.isEmpty() || employmentType == null) {
            return null;
        }
        String type = employmentType.trim().toUpperCase(Locale.ROOT);
        return KNOWN_EMPLOYMENT_TYPES.contains(type) ? wanted.contains(type) : null;
    }

    /** Only "pays at least what they ask" counts: earning more than the expected maximum is no mismatch. */
    private static Boolean salaryMatches(UserJobPreference preference, JobPreferenceFacts job) {
        Long offered = job.maxSalary() != null ? job.maxSalary() : job.minSalary();
        if (preference.getExpectedSalaryMin() == null || offered == null
            || !preference.getCurrency().name().equalsIgnoreCase(job.currency())
            || !preference.getSalaryPeriod().name().equalsIgnoreCase(job.salaryPeriod())) {
            return null;
        }
        if (offered >= preference.getExpectedSalaryMin()) {
            return true;
        }
        // Open to negotiation: a lower offer does not rule the job out. With no maximum, it is only a starting salary.
        return preference.isSalaryNegotiable() || job.maxSalary() == null ? null : false;
    }

    private static Set<String> words(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}\\p{N}+#.]+"))
            .filter(word -> !word.isBlank())
            .collect(Collectors.toSet());
    }
}
