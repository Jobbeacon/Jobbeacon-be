package com.jobbeacon.dto.response.admin;

public record AdminUserStatsResponse(long total, long admins, long active, long suspended, long withCv) {
}
