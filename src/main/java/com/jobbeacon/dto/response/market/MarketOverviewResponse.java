package com.jobbeacon.dto.response.market;

import java.time.LocalDate;
import java.util.List;

/**
 * Market statistics over the indexed jobs that match the filters. Salaries are VND per month: the middle of each
 * job's range, USD converted at {@code usdVndRate}, outside 2 to 300 million left out.
 *
 * @param totalJobs      jobs that match the filters
 * @param jobsWithSalary those of them with a usable salary
 * @param minSalarySample groups with fewer salaried jobs than this have no salary figures
 */
public record MarketOverviewResponse(
        long totalJobs,
        long jobsWithSalary,
        SalaryStats salary,
        List<Group> topSkills,
        List<Group> byProvince,
        List<Group> byCategory,
        List<Group> byExperience,
        List<TrendPoint> postingTrend,
        double usdVndRate,
        int minSalarySample
) {

    /**
     * Salary quartiles in VND per month. All three are null when {@code sampleSize} is below the minimum sample.
     */
    public record SalaryStats(Long p25, Long median, Long p75, long sampleSize) {
    }

    /**
     * @param share fraction of {@code totalJobs} in this group; groups overlap (a job has several skills)
     */
    public record Group(String id, String name, long jobCount, double share, SalaryStats salary) {
    }

    /** Jobs posted in the week starting on {@code week} (Monday). */
    public record TrendPoint(LocalDate week, long jobCount) {
    }
}
