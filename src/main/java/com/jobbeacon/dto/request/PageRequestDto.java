package com.jobbeacon.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PageRequestDto {

    @JsonProperty("page")
    private int page;

    @JsonProperty("size")
    private int size;

    @JsonProperty("sort_by")
    private String sortBy;

    @JsonProperty("sort_type")
    private String sortType;

}
