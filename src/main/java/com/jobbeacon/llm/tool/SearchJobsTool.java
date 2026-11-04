package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.service.JobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Component
public class SearchJobsTool implements LlmTool {

    public static final String NAME = "search_jobs";

    private static final String IGNORED_FILTERS = "ignored_filters";
    private static final String COMPACT_NOTE = "Danh sách đã hiển thị cho user theo thứ tự này";

    private final JobService jobService;
    private final SearchFilterValues filterValues;
    private final int defaultSize;
    private final int maxSize;

    public SearchJobsTool(
            JobService jobService,
            SearchFilterValues filterValues,
            @Value("${app.chat.search.default-size:6}") int defaultSize,
            @Value("${app.chat.search.max-size:10}") int maxSize
    ) {
        this.jobService = jobService;
        this.filterValues = filterValues;
        this.maxSize = Math.max(maxSize, 1);
        this.defaultSize = Math.clamp(defaultSize, 1, this.maxSize);
    }

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
        properties.put("province", arrayOf(filterValues.provinces(),
                "Tỉnh/thành phố làm việc, chọn một hoặc nhiều giá trị trong danh sách. Quy đổi cách gọi của "
                        + "người dùng về đúng tên, ví dụ \"Sài Gòn\", \"HCM\", \"TP.HCM\" → \"Hồ Chí Minh\"; "
                        + "\"HN\" → \"Hà Nội\". Bỏ trống nếu người dùng không nêu địa điểm."));
        properties.put("categories", arrayOf(filterValues.rootCategories(),
                "Ngành nghề (ngành cấp 1, đã gồm các ngành con), chọn trong danh sách. Ví dụ \"lập trình\", "
                        + "\"IT\" → \"Công nghệ Thông tin\". Chỉ dùng khi người dùng nêu rõ ngành; "
                        + "chức danh cụ thể thì đưa vào keywords."));
        properties.put("employmentTypes", arrayOf(filterValues.employmentTypes(),
                "Hình thức làm việc: FULL_TIME (toàn thời gian, fulltime), PART_TIME (bán thời gian, "
                        + "parttime), INTERN (thực tập, intern)."));
        properties.put("postedWithin", stringOf(filterValues.postedWithin(),
                "Thời gian đăng tin: 24h (hôm nay, trong ngày), 7d (tuần này, mấy ngày gần đây), "
                        + "30d (tháng này, gần đây)."));
        properties.put("sources", arrayOf(filterValues.sources(),
                "Trang nguồn tin tuyển dụng (tên miền), ví dụ \"itviec\" → \"itviec.com\". "
                        + "Chỉ dùng khi người dùng yêu cầu nguồn cụ thể."));
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
        properties.put("sortBy", stringOf(filterValues.sortBy(),
                "Cách sắp xếp: relevance (phù hợp nhất với keywords), posted_date (mới đăng nhất), "
                        + "max_salary (lương cao nhất). Mặc định relevance khi có keywords, ngược lại posted_date."));
        properties.put("size", Schema.builder()
                .type(Type.Known.INTEGER)
                .minimum(1.0)
                .maximum((double) maxSize)
                .description("Số tin mỗi trang, tối đa " + maxSize + ". Mặc định " + defaultSize + ".")
                .build());

        return FunctionDeclaration.builder()
                .name(NAME)
                .description("Tìm kiếm việc làm theo yêu cầu của người dùng. Trả về danh sách tin tuyển dụng "
                        + "(id, chức danh, công ty, mức lương, địa điểm, ngày đăng) và tổng số kết quả. "
                        + "Không có currency nghĩa là lương tính bằng VND. "
                        + "Bộ lọc có giá trị không hợp lệ sẽ bị bỏ qua và liệt kê trong ignored_filters. "
                        + "Dùng id với get_job_detail để xem chi tiết.")
                .parameters(Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(properties))
                .build();
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Map<String, List<String>> ignored = new LinkedHashMap<>();
        JobSearchRequest request = new JobSearchRequest();
        request.setKeyword(LlmToolArgs.string(args, "keywords"));
        request.setSkills(joinOrNull(LlmToolArgs.strings(args, "skills")));
        request.setLocations(joinOrNull(matchAll(args, "province", filterValues::matchProvince, ignored)));
        request.setCategories(joinOrNull(matchAll(args, "categories", filterValues::matchRootCategory, ignored)));
        request.setEmploymentTypes(joinOrNull(
                matchAll(args, "employmentTypes", filterValues::matchEmploymentType, ignored)));
        request.setPostedWithin(matchOne(args, "postedWithin", filterValues::matchPostedWithin, ignored));
        request.setSources(joinOrNull(matchAll(args, "sources", filterValues::matchSource, ignored)));
        request.setMinSalary(LlmToolArgs.longValue(args, "minSalary"));
        request.setMaxSalary(LlmToolArgs.longValue(args, "maxSalary"));
        request.setPage(Math.max(LlmToolArgs.intValue(args, "page", 1), 1));
        request.setSize(Math.clamp(LlmToolArgs.intValue(args, "size", defaultSize), 1, maxSize));
        String sortBy = matchOne(args, "sortBy", filterValues::matchSortBy, ignored);
        if (sortBy != null) {
            request.setSortBy(sortBy);
        } else if (request.getKeyword() != null) {
            request.setSortBy(SearchFilterValues.SORT_RELEVANCE);
        }

        PageResponse<JobSummaryResponse> result = jobService.searchJobs(request).getData();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.getAmount());
        response.put("page", request.getPage());
        response.put("size", request.getSize());
        response.put("jobs", result.getContent().stream().map(this::toItem).toList());
        if (!ignored.isEmpty()) {
            response.put(IGNORED_FILTERS, ignored);
        }
        return response;
    }

    @Override
    public Map<String, Object> compact(Map<String, Object> result) {
        List<String> jobIds = result.get("jobs") instanceof List<?> jobs
                ? jobs.stream()
                        .filter(Map.class::isInstance)
                        .map(job -> LlmToolArgs.id(((Map<?, ?>) job).get("id")))
                        .filter(Objects::nonNull)
                        .toList()
                : List.of();
        Map<String, Object> compacted = new LinkedHashMap<>();
        compacted.put("total", result.get("total"));
        compacted.put("page", result.get("page"));
        compacted.put("size", result.get("size"));
        compacted.put("job_ids", jobIds);
        LlmToolArgs.putIfNotNull(compacted, IGNORED_FILTERS, result.get(IGNORED_FILTERS));
        compacted.put("note", COMPACT_NOTE);
        return compacted;
    }

    private Map<String, Object> toItem(JobSummaryResponse job) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", LlmToolArgs.id(job.id()));
        item.put("title", job.title());
        item.put("company", job.companyName());
        LlmToolArgs.putIfNotNull(item, "min_salary", job.minSalary());
        LlmToolArgs.putIfNotNull(item, "max_salary", job.maxSalary());
        LlmToolArgs.putCurrencyIfNotDefault(item, job.currency());
        if (job.location() != null && !job.location().isEmpty()) {
            item.put("location", job.location());
        }
        LlmToolArgs.putIfNotNull(item, "posted_date", LlmToolArgs.date(job.postedDate()));
        return item;
    }

    private static Schema arrayOf(List<String> allowed, String description) {
        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(stringOf(allowed, null))
                .description(description)
                .build();
    }

    private static Schema stringOf(List<String> allowed, String description) {
        Schema.Builder builder = Schema.builder().type(Type.Known.STRING);
        if (!allowed.isEmpty()) {
            builder.enum_(allowed);
        }
        if (description != null) {
            builder.description(description);
        }
        return builder.build();
    }

    /** Canonical values of a filter; values outside the allowed set are recorded in {@code ignored}. */
    private static List<String> matchAll(Map<String, Object> args, String key,
                                         Function<String, Optional<String>> matcher,
                                         Map<String, List<String>> ignored) {
        List<String> matched = new ArrayList<>();
        for (String value : LlmToolArgs.strings(args, key)) {
            matcher.apply(value).ifPresentOrElse(
                    canonical -> {
                        if (!matched.contains(canonical)) {
                            matched.add(canonical);
                        }
                    },
                    () -> ignored.computeIfAbsent(key, k -> new ArrayList<>()).add(value));
        }
        return matched;
    }

    private static String matchOne(Map<String, Object> args, String key,
                                   Function<String, Optional<String>> matcher,
                                   Map<String, List<String>> ignored) {
        String value = LlmToolArgs.string(args, key);
        if (value == null) {
            return null;
        }
        Optional<String> matched = matcher.apply(value);
        if (matched.isEmpty()) {
            ignored.put(key, List.of(value));
        }
        return matched.orElse(null);
    }

    private String joinOrNull(List<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }
}
