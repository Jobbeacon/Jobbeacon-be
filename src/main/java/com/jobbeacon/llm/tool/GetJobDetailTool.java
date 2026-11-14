package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.jobbeacon.dto.response.CategoryResponse;
import com.jobbeacon.dto.response.JobDetailResponse;
import com.jobbeacon.dto.response.JobSourceResponse;
import com.jobbeacon.dto.response.SkillResponse;
import com.jobbeacon.service.JobService;
import com.jobbeacon.utils.HtmlTextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
public class GetJobDetailTool implements LlmTool {

    public static final String NAME = "get_job_detail";

    private static final String COMPACT_NOTE = "Đã xem chi tiết; gọi lại get_job_detail nếu cần thông tin cụ thể";

    private final JobService jobService;
    private final int descriptionMaxChars;
    private final int benefitsMaxChars;

    public GetJobDetailTool(
            JobService jobService,
            @Value("${app.chat.detail.description-max-chars:2000}") int descriptionMaxChars,
            @Value("${app.chat.detail.benefits-max-chars:800}") int benefitsMaxChars
    ) {
        this.jobService = jobService;
        this.descriptionMaxChars = Math.max(descriptionMaxChars, 1);
        this.benefitsMaxChars = Math.max(benefitsMaxChars, 1);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FunctionDeclaration declaration() {
        return FunctionDeclaration.builder()
                .name(NAME)
                .description("Lấy thông tin chi tiết một tin tuyển dụng: mô tả công việc (gồm yêu cầu kinh nghiệm), "
                        + "mức lương, phúc lợi, kỹ năng, ngành nghề, địa điểm và tên miền nguồn tin. "
                        + "Không có currency nghĩa là lương tính bằng VND.")
                .parameters(Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(Map.of("jobId", Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Id (UUID) của tin tuyển dụng, có thể lấy từ kết quả search_jobs.")
                                .build()))
                        .required("jobId"))
                .build();
    }

    @Override
    public Object execute(Map<String, Object> args) {
        JobDetailResponse job = jobService.getJobDetail(LlmToolArgs.uuid(args, "jobId")).getData();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", LlmToolArgs.id(job.id()));
        detail.put("title", job.title());
        detail.put("company", job.company() == null ? null : job.company().name());
        LlmToolArgs.putIfNotNull(detail, "employment_type", job.employmentType());
        LlmToolArgs.putIfNotNull(detail, "min_salary", job.minSalary());
        LlmToolArgs.putIfNotNull(detail, "max_salary", job.maxSalary());
        LlmToolArgs.putCurrencyIfNotDefault(detail, job.currency());
        LlmToolArgs.putIfNotNull(detail, "salary_period", job.salaryPeriod());
        putIfNotEmpty(detail, "location", job.location());
        LlmToolArgs.putIfNotNull(detail, "posted_date", LlmToolArgs.date(job.postedDate()));
        LlmToolArgs.putIfNotNull(detail, "expired_date", LlmToolArgs.date(job.expiredDate()));
        putIfNotEmpty(detail, "skills", names(job.skills(), SkillResponse::name));
        putIfNotEmpty(detail, "categories", names(job.categories(), CategoryResponse::name));
        putIfNotEmpty(detail, "sources", names(job.jobSources(), JobSourceResponse::domain));
        LlmToolArgs.putIfNotNull(detail, "description", plainText(job.description(), descriptionMaxChars));
        LlmToolArgs.putIfNotNull(detail, "benefits", plainText(job.benefits(), benefitsMaxChars));
        return detail;
    }

    @Override
    public Map<String, Object> compact(Map<String, Object> result) {
        Map<String, Object> compacted = new LinkedHashMap<>();
        compacted.put("id", result.get("id"));
        compacted.put("title", result.get("title"));
        compacted.put("company", result.get("company"));
        compacted.put("note", COMPACT_NOTE);
        return compacted;
    }

    private String plainText(String html, int maxChars) {
        if (html == null) {
            return null;
        }
        return LlmToolArgs.truncate(HtmlTextUtils.toPlainText(html), maxChars);
    }

    private <T> List<String> names(List<T> items, Function<T, String> name) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(name)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private void putIfNotEmpty(Map<String, Object> map, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            map.put(key, values);
        }
    }
}
