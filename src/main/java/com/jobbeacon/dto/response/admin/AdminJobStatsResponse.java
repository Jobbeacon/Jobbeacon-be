package com.jobbeacon.dto.response.admin;

public record AdminJobStatsResponse(long total, long open, long expired, long crawledToday) {
}
