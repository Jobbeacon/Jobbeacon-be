package com.jobbeacon.service.impl;

import com.jobbeacon.configuration.JobDedupProperties;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLshBand;
import com.jobbeacon.repository.JobLshBandRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.service.JobDeduplicationService;
import com.jobbeacon.utils.dedup.ContentHasher;
import com.jobbeacon.utils.dedup.JobTitleNormalizer;
import com.jobbeacon.utils.dedup.MinHasher;
import com.jobbeacon.utils.dedup.ShingleSimilarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDeduplicationServiceImpl implements JobDeduplicationService {

    private final JobRepository jobRepository;
    private final JobLshBandRepository jobLshBandRepository;
    private final JobDedupProperties properties;

    @Override
    public JobFingerprint fingerprint(String title, String description) {
        String titleNormalized = JobTitleNormalizer.normalize(title);
        JobTitleNormalizer.Seniority seniority = JobTitleNormalizer.seniority(title).orElse(null);
        String descriptionNormalized = ShingleSimilarity.normalize(description);
        if (descriptionNormalized.isEmpty()) {
            return new JobFingerprint(titleNormalized, seniority, null, null, new long[0], null);
        }
        int[] minhash = MinHasher.signature(description);
        return new JobFingerprint(titleNormalized, seniority, ContentHasher.hash(description), minhash,
                MinHasher.bands(minhash), descriptionNormalized);
    }

    @Override
    public Optional<Job> findDuplicate(UUID companyId, JobFingerprint fp, Instant postedDate,
                                       Long minSalary, Long maxSalary) {
        if (!properties.enabled() || companyId == null || fp == null) {
            return Optional.empty();
        }
        Instant anchor = postedDate != null ? postedDate : Instant.now();
        Duration window = Duration.ofDays(properties.windowDays());
        Instant from = anchor.minus(window);
        Instant to = anchor.plus(window);

        Set<String> containmentShingles =
                ShingleSimilarity.shingles(fp.descriptionNormalized(), ShingleSimilarity.CONTAINMENT_SHINGLE);
        List<Scored> candidates = findByHash(companyId, fp, containmentShingles, from, to).stream()
                .filter(c -> passesBlockingRules(c.job(), fp, minSalary, maxSalary))
                .toList();
        if (candidates.isEmpty() && fp.bands() != null && fp.bands().length > 0 && fp.minhash() != null) {
            candidates = findByLsh(companyId, fp, containmentShingles, from, to).stream()
                    .filter(c -> passesBlockingRules(c.job(), fp, minSalary, maxSalary))
                    .toList();
        }

        Optional<Scored> best = candidates.stream()
                .max(Comparator.comparingDouble(Scored::score)
                        .thenComparing(c -> c.job().getId().toString(), Comparator.reverseOrder()));
        if (best.isPresent()) {
            Scored s = best.get();
            log.info("(findDuplicate) duplicate jobId: {}, rule: {}, content: {}, title: {}, score: {}",
                    s.job().getId(), s.rule(), s.content(), s.title(), s.score());
        } else {
            log.debug("(findDuplicate) no duplicate for companyId: {}", companyId);
        }
        return best.map(Scored::job);
    }

    @Override
    @Transactional
    public void saveBands(UUID jobId, long[] bands) {
        jobLshBandRepository.deleteByJobId(jobId);
        if (bands == null || bands.length == 0) {
            return;
        }
        List<JobLshBand> rows = new ArrayList<>(bands.length);
        for (int i = 0; i < bands.length; i++) {
            rows.add(JobLshBand.builder()
                    .id(new JobLshBand.JobLshBandId(jobId, (short) i))
                    .bandHash(bands[i])
                    .build());
        }
        jobLshBandRepository.saveAll(rows);
    }

    private List<Scored> findByHash(UUID companyId, JobFingerprint fp, Set<String> containmentShingles,
                                    Instant from, Instant to) {
        if (fp.contentHash() == null) {
            return List.of();
        }
        List<Scored> result = new ArrayList<>();
        for (Job job : jobRepository.findByCompanyIdAndContentHash(companyId, fp.contentHash())) {
            if (!inWindow(job.getPostedDate(), from, to)) {
                log.debug("(findDuplicate) rejected jobId: {}, rule: hash, outside window", job.getId());
                continue;
            }
            double title = JobTitleNormalizer.tokenSimilarity(fp.titleNormalized(), titleOf(job));
            String other = ShingleSimilarity.normalize(job.getDescription());
            int shingleCount = containmentShingles.size();
            if (!other.isEmpty()) {
                shingleCount = Math.min(shingleCount,
                        ShingleSimilarity.shingles(other, ShingleSimilarity.CONTAINMENT_SHINGLE).size());
            }
            if (shingleCount < properties.shortTextShingles() && title < properties.titleThreshold()) {
                log.info("(findDuplicate) rejected jobId: {}, rule: hash, short text with different title, "
                        + "minShingles: {}, title: {}", job.getId(), shingleCount, title);
                continue;
            }
            result.add(new Scored(job, "hash", 1.0, title));
        }
        return result;
    }

    private List<Scored> findByLsh(UUID companyId, JobFingerprint fp, Set<String> containmentShingles,
                                   Instant from, Instant to) {
        long[] bands = fp.bands();
        List<Long> bandHashes = new ArrayList<>(bands.length);
        List<String> bandKeys = new ArrayList<>(bands.length);
        for (int i = 0; i < bands.length; i++) {
            bandHashes.add(bands[i]);
            bandKeys.add(i + ":" + bands[i]);
        }
        List<UUID> ids = jobLshBandRepository.findCandidateJobIds(bandHashes, bandKeys, companyId, from, to);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> shingles = ShingleSimilarity.shingles(fp.descriptionNormalized(), MinHasher.SHINGLE);
        List<Scored> result = new ArrayList<>();
        for (Job job : jobRepository.findByIdIn(ids)) {
            int[] other = job.getMinhash();
            if (other == null || other.length != MinHasher.NUM_HASHES) {
                continue;
            }
            double title = JobTitleNormalizer.tokenSimilarity(fp.titleNormalized(), titleOf(job));
            Scored match = matchLsh(job, fp, shingles, containmentShingles, title);
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    /**
     * Content is the exact Jaccard of the shingle sets; MinHash only finds candidates and is used as an estimate when the
     * candidate has no description. Rules:
     * <ul>
     * <li>a: near-identical content, title ignored. Two postings of the same company with completely different titles
     * are merged when their descriptions are nearly identical (only the blocking rules can prevent it);</li>
     * <li>b: high content, relaxed title;</li>
     * <li>c: content and title;</li>
     * <li>d: the shorter description is contained in the longer one, with a similar title.</li>
     * </ul>
     * A short text on either side, or a candidate without description, makes a and b require the strict title threshold
     * (the hash rule does the same for short texts).
     */
    private Scored matchLsh(Job job, JobFingerprint fp, Set<String> shingles, Set<String> containmentShingles,
                            double title) {
        String other = ShingleSimilarity.normalize(job.getDescription());
        double content = other.isEmpty()
                ? MinHasher.estimateJaccard(fp.minhash(), job.getMinhash())
                : ShingleSimilarity.jaccard(shingles, ShingleSimilarity.shingles(other, MinHasher.SHINGLE));
        Set<String> otherShingles = ShingleSimilarity.shingles(other, ShingleSimilarity.CONTAINMENT_SHINGLE);
        int minShingles = Math.min(containmentShingles.size(), otherShingles.size());
        boolean titleRequired = minShingles < properties.shortTextShingles();
        boolean titleAllowed = !titleRequired || title >= properties.titleThreshold();
        String rule = null;
        if (content >= properties.noTitleThreshold() && titleAllowed) {
            rule = "a";
        } else if (content >= properties.highContentThreshold() && title >= properties.relaxedTitleThreshold()
                && titleAllowed) {
            rule = "b";
        } else if (content >= properties.jaccardThreshold() && title >= properties.titleThreshold()) {
            rule = "c";
        }
        if (rule != null) {
            log.info("(findDuplicate) lsh match jobId: {}, rule: {}, content: {}, title: {}",
                    job.getId(), rule, content, title);
            return new Scored(job, rule, content, title);
        }
        if (!titleAllowed && content >= properties.highContentThreshold()) {
            log.info("(findDuplicate) rules a, b skipped for jobId: {}, {} with different title, content: {}, "
                            + "minShingles: {}, title: {}", job.getId(),
                    other.isEmpty() ? "candidate without description" : "short text", content, minShingles, title);
        }
        if (title < properties.containmentTitleThreshold()) {
            log.debug("(findDuplicate) lsh no match jobId: {}, content: {}, title: {}", job.getId(), content, title);
            return null;
        }
        double containment = ShingleSimilarity.containment(containmentShingles, otherShingles);
        if (containment < properties.containmentThreshold() || minShingles < properties.containmentMinShingles()) {
            log.debug("(findDuplicate) lsh no match jobId: {}, content: {}, title: {}, containment: {}, minShingles: {}",
                    job.getId(), content, title, containment, minShingles);
            return null;
        }
        log.info("(findDuplicate) lsh match jobId: {}, rule: d, content: {}, title: {}, containment: {}, minShingles: {}",
                job.getId(), content, title, containment, minShingles);
        return new Scored(job, "d", containment, title);
    }

    private boolean passesBlockingRules(Job job, JobFingerprint fp, Long minSalary, Long maxSalary) {
        if (fp.seniority() != null) {
            JobTitleNormalizer.Seniority other = JobTitleNormalizer.seniority(job.getTitle()).orElse(null);
            if (other != null && other != fp.seniority()) {
                log.debug("(findDuplicate) rejected jobId: {}, seniority {} vs {}", job.getId(), fp.seniority(), other);
                return false;
            }
        }
        if (salaryDiffers(minSalary, job.getMinSalary()) || salaryDiffers(maxSalary, job.getMaxSalary())) {
            log.debug("(findDuplicate) rejected jobId: {}, salary mismatch", job.getId());
            return false;
        }
        return true;
    }

    private boolean salaryDiffers(Long a, Long b) {
        if (a == null || b == null) {
            return false;
        }
        long max = Math.max(Math.abs(a), Math.abs(b));
        if (max == 0) {
            return false;
        }
        return (double) Math.abs(a - b) / max > properties.salaryTolerance();
    }

    private static boolean inWindow(Instant posted, Instant from, Instant to) {
        return posted == null || (!posted.isBefore(from) && !posted.isAfter(to));
    }

    private static String titleOf(Job job) {
        return job.getTitleNormalized() != null ? job.getTitleNormalized() : JobTitleNormalizer.normalize(job.getTitle());
    }

    private record Scored(Job job, String rule, double content, double title) {
        double score() {
            return 0.5 * content + 0.5 * title;
        }
    }
}
