package com.jobbeacon.dto.response;

import java.util.UUID;

public record CompanyResponse (
    UUID id,
    String name,
    String logoUrl
) {
}
