package com.jobbeacon.dto.response;

import java.util.List;
import java.util.UUID;

public record CategoryResponse (
    UUID id,
    String name,
    Integer level,
    List<CategoryResponse> children
) {
}
