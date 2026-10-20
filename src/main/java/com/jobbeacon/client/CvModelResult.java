package com.jobbeacon.client;

import com.jobbeacon.dto.cv.CvParsedPayload;

/**
 * Raw model output (not yet cross-checked) plus usage info for cost tracking.
 * Token counts are null when the API does not report them.
 */
public record CvModelResult(
    CvParsedPayload payload,
    String model,
    Integer promptTokens,
    Integer candidateTokens,
    Integer thoughtsTokens,
    long latencyMs
) {
}
