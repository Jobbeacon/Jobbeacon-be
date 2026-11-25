package com.jobbeacon.dto.response.admin;

import java.util.List;
import java.util.UUID;

public record AdminCategoryNodeResponse(
    UUID id,
    String name,
    int level,
    long jobCount,
    List<AdminCategoryNodeResponse> children
) {
}
