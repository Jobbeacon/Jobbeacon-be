package com.jobbeacon.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class JobSummaryResponse {
    private UUID id;
    private String title;
    private String companyName;
    private Long minSalary;
    private Long maxSalary;
    private String location;
    private String currency;

}
