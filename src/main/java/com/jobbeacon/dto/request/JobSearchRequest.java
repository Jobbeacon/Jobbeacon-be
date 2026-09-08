package com.jobbeacon.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class JobSearchRequest extends PageRequestDto {
    private String keyword;
}
