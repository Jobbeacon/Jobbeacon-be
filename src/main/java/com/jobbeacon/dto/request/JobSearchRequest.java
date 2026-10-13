package com.jobbeacon.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class JobSearchRequest extends PageRequestDto {
    private String keyword;

    /** Địa điểm gõ ở thanh tìm kiếm. */
    private String location;

    /** Địa điểm chọn ở bộ lọc, phân tách bằng dấu phẩy. */
    private String locations;

    /** Ngành nghề, phân tách bằng dấu phẩy. */
    private String categories;

    /** Kỹ năng, phân tách bằng dấu phẩy. */
    private String skills;

    @JsonProperty("min_salary")
    private Long minSalary;

    @JsonProperty("max_salary")
    private Long maxSalary;

    /** none, y1_3, y3_5, y5. Nhiều giá trị phân tách bằng dấu phẩy. */
    private String experience;

    /** FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP. */
    @JsonProperty("employment_types")
    private String employmentTypes;

    /** 24h, 7d, 30d. Nhiều giá trị thì lấy khoảng rộng nhất. */
    @JsonProperty("posted_within")
    private String postedWithin;

    /** Domain nguồn, ví dụ itviec.com. */
    private String sources;
}
