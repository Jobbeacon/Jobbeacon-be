package com.jobbeacon.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingMessage {
    private String url;
    private String companyName;
    private String companyLogoUrl;
    private String title;
    private String description;
    private int experienceMonths;
    private String employmentType;
    private LocalDateTime postedDate;
    private LocalDateTime expiredDate;
    private String location;
    private Long minSalary;
    private Long maxSalary;
    private String currency;
    private String salaryPeriod;
    private String industry;
    private List<String> skills;
    private String benefits;
    private LocalDateTime crawledAt;
}
