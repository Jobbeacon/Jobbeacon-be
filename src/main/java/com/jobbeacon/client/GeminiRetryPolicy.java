package com.jobbeacon.client;

/**
 * rotateKeyOnRateLimit = true (chat): 429 switches to the next key without retrying on the current one, and
 * non-retryable errors are thrown immediately except 401/403, which still switch to the next key.
 */
public record GeminiRetryPolicy(int maxRetries,
                                long initialDelayMs,
                                double multiplier,
                                boolean rotateKeyOnRateLimit) {
}
