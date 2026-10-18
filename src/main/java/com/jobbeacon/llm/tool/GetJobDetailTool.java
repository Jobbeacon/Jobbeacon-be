package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.jobbeacon.dto.response.JobDetailResponse;
import com.jobbeacon.service.JobService;
import com.jobbeacon.utils.HtmlTextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetJobDetailTool implements LlmTool {

    public static final String NAME = "get_job_detail";

    private static final int MAX_TEXT_LENGTH = 4000;

    private final JobService jobService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FunctionDeclaration declaration() {
        return FunctionDeclaration.builder()
                .name(NAME)
                .description("Lấy thông tin chi tiết một tin tuyển dụng: mô tả công việc, yêu cầu kinh nghiệm, "
                        + "mức lương, phúc lợi, kỹ năng, ngành nghề, địa điểm và link tin gốc.")
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
        return new JobDetailResponse(
                job.id(),
                job.company(),
                job.title(),
                plainText(job.description()),
                job.experienceMonths(),
                job.employmentType(),
                job.postedDate(),
                job.expiredDate(),
                job.minSalary(),
                job.maxSalary(),
                job.currency(),
                job.salaryPeriod(),
                job.location(),
                plainText(job.benefits()),
                job.skills(),
                job.categories(),
                job.jobSources()
        );
    }

    private String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = HtmlTextUtils.toPlainText(html);
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
