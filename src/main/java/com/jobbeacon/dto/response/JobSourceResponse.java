package com.jobbeacon.dto.response;

import java.util.UUID;

public record JobSourceResponse (
    UUID id,
    String domain,
    String jobUrl
) {
}
