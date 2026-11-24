package com.jobbeacon.client;

/** How often all models and keys are tried again after every one of them failed, and how long to wait in between. */
public record GeminiRetryPolicy(int maxRetries,
                                long initialDelayMs,
                                double multiplier) {
}
