package com.jobbeacon.internal;

import com.jobbeacon.constant.EmploymentType;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse.CvTitle;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse.PreferenceMatch;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.repository.CvProfileRepository;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.TitleMatchRepository;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import com.jobbeacon.repository.UserSavedJobRepository;
import com.jobbeacon.repository.projection.JobPreferenceFacts;
import com.jobbeacon.repository.projection.JobProvince;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Compares jobs with what a user asked for and what their CV says, on behalf of the recommendation service. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalRecommendationService {

    private static final Set<String> KNOWN_EMPLOYMENT_TYPES =
        Arrays.stream(EmploymentType.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final UserSavedJobRepository userSavedJobRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final CvProfileRepository cvProfileRepository;
    private final JobRepository jobRepository;
    private final JobLocationRepository jobLocationRepository;
    private final TitleMatchRepository titleMatchRepository;

    @Value("${app.title-matching.enabled}")
    private boolean matchByMeaning;

    public InternalRecommendationContextResponse context(UUID userId, Collection<UUID> jobIds) {
        List<UUID> savedJobIds = userSavedJobRepository.findJobIdsByUserId(userId);
        List<PreferenceMatch> matches = jobIds.isEmpty() ? List.of() : matches(userId, jobIds);
        return new InternalRecommendationContextResponse(savedJobIds, matches);
    }

    /** Titles to look for jobs with, for {@link TitleJobService}. */
    UserTitles titles(UUID userId) {
        return UserTitles.of(
            userJobPreferenceRepository.findByUserId(userId).orElse(null),
            cvProfileRepository.findByUserId(userId).orElse(null));
    }

    private List<PreferenceMatch> matches(UUID userId, Collection<UUID> jobIds) {
        UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
        CvProfile cv = cvProfileRepository.findByUserId(userId).orElse(null);
        UserTitles titles = UserTitles.of(preference, cv);
        Integer experience = cv == null ? null : cv.getTotalMonthsExperience();
        if (preference == null && titles.isEmpty() && experience == null) {
            return List.of();
        }
        Map<UUID, Set<String>> matchingTitles = matchByMeaning
            ? titleMatchRepository.findMatchingTitles(titles.all(), jobIds)
            : Map.of();
        Set<String> wantedProvinces = preference == null
            ? Set.of()
            : preference.getProvinces().stream().map(Province::getId).collect(Collectors.toSet());
        List<String> wantedTypes = preference == null ? null : preference.getEmploymentTypes();
        Map<UUID, Set<String>> provincesByJob = wantedProvinces.isEmpty()
            ? Map.of()
            : jobLocationRepository.findProvincesByJobIds(jobIds).stream().collect(Collectors.groupingBy(
                JobProvince::jobId, Collectors.mapping(JobProvince::provinceId, Collectors.toSet())));
        return jobRepository.findPreferenceFactsByIds(jobIds).stream()
            .map(job -> new PreferenceMatch(
                job.jobId(),
                titleMatches(titles, job, matchingTitles),
                locationMatches(wantedProvinces, provincesByJob.getOrDefault(job.jobId(), Set.of())),
                employmentTypeMatches(wantedTypes, job.employmentType()),
                preference == null ? null : salaryMatches(preference, job),
                cvTitle(titles, job, matchingTitles),
                missingExperience(experience, job.experienceMonths())))
            .toList();
    }

    private static Boolean titleMatches(UserTitles titles, JobPreferenceFacts job, Map<UUID, Set<String>> matchingTitles) {
        return titles.desired().isEmpty() || job.title() == null ? null : matches(titles.desired(), job, matchingTitles);
    }

    private static CvTitle cvTitle(UserTitles titles, JobPreferenceFacts job, Map<UUID, Set<String>> matchingTitles) {
        if (matches(titles.current(), job, matchingTitles)) {
            return CvTitle.CURRENT;
        }
        return matches(titles.past(), job, matchingTitles) ? CvTitle.PAST : null;
    }

    private static boolean matches(List<String> titles, JobPreferenceFacts job, Map<UUID, Set<String>> matchingTitles) {
        return UserTitles.anyMatches(titles, job.title())
            || !Collections.disjoint(titles, matchingTitles.getOrDefault(job.jobId(), Set.of()));
    }

    /** The crawler stores 0 both for "no experience required" and for "not stated", so 0 tells nothing. */
    private static Integer missingExperience(Integer experience, Integer required) {
        return experience == null || required == null || required <= 0 ? null : Math.max(0, required - experience);
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
}
