package com.jobbeacon.dto.response;

import java.util.List;
import java.util.UUID;

public record CategoryNodeResponse(UUID id, String name, List<CategoryNodeResponse> children) {
}
