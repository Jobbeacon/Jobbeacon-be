package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchJobsTool implements LlmTool {

    public static final String NAME = "search_jobs";

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;

    private final JobService jobService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FunctionDeclaration declaration() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("keywords", Schema.builder()
                .type(Type.Known.STRING)
                .description("Từ khóa tìm kiếm: chức danh, vị trí hoặc tên công ty, ví dụ \"Java backend\".")
                .build());
        properties.put("skills", Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING))
                .description("Kỹ năng yêu cầu, ví dụ [\"Java\", \"Spring Boot\"]. Tin khớp ít nhất một kỹ năng.")
                .build());
        properties.put("province", Schema.builder()
                .type(Type.Known.STRING)
                .description("Tỉnh/thành phố làm việc, ví dụ \"Hà Nội\", \"Hồ Chí Minh\".")
                .build());
        properties.put("minSalary", Schema.builder()
                .type(Type.Known.INTEGER)
                .minimum(0.0)
                .description("Mức lương tối thiểu mong muốn, đơn vị VND/tháng, ví dụ 20000000.")
                .build());
        properties.put("maxSalary", Schema.builder()
                .type(Type.Known.INTEGER)
                .minimum(0.0)
                .description("Mức lương tối đa, đơn vị VND/tháng.")
                .build());
        properties.put("page", Schema.builder()
                .type(Type.Known.INTEGER)
                .minimum(1.0)
                .description("Số trang, bắt đầu từ 1. Mặc định 1.")
                .build());
        properties.put("size", Schema.builder()
                .type(Type.Known.INTEGER)
                .minimum(1.0)
                .maximum((double) MAX_SIZE)
                .description("Số tin mỗi trang, tối đa " + MAX_SIZE + ". Mặc định " + DEFAULT_SIZE + ".")
                .build());

        return FunctionDeclaration.builder()
                .name(NAME)
                .description("Tìm kiếm việc làm theo yêu cầu của người dùng. Trả về danh sách tin tuyển dụng "
                        + "(id, chức danh, công ty, mức lương, địa điểm, ngày đăng) và tổng số kết quả. "
                        + "Dùng id với get_job_detail để xem chi tiết.")
                .parameters(Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(properties))
                .build();
    }

    @Override
    public Object execute(Map<String, Object> args) {
        JobSearchRequest request = new JobSearchRequest();
        request.setKeyword(LlmToolArgs.string(args, "keywords"));
        request.setSkills(joinOrNull(LlmToolArgs.strings(args, "skills")));
        request.setLocations(LlmToolArgs.string(args, "province"));
        request.setMinSalary(LlmToolArgs.longValue(args, "minSalary"));
        request.setMaxSalary(LlmToolArgs.longValue(args, "maxSalary"));
        request.setPage(Math.max(LlmToolArgs.intValue(args, "page", 1), 1));
        request.setSize(Math.clamp(LlmToolArgs.intValue(args, "size", DEFAULT_SIZE), 1, MAX_SIZE));
        if (request.getKeyword() != null) {
            request.setSortBy("relevance");
        }

        PageResponse<JobSummaryResponse> result = jobService.searchJobs(request).getData();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.getAmount());
        response.put("page", request.getPage());
        response.put("size", request.getSize());
        response.put("jobs", result.getContent());
        return response;
    }

    private String joinOrNull(List<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }
}
