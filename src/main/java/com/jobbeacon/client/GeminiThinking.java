package com.jobbeacon.client;

import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;

import java.util.Optional;

/** Thinking settings differ by model family, and a setting the model does not know is answered with 400. */
final class GeminiThinking {

    private static final String GEMINI_3_PREFIX = "gemini-3";
    private static final String GEMINI_2_5_PREFIX = "gemini-2.5";

    private GeminiThinking() {
    }

    /**
     * Gemini 3 takes a level (a budget of 0 becomes the lowest level), Gemini 2.5 takes a budget, Gemma takes neither.
     *
     * @param level  null = not set
     * @param budget null or negative = not set, 0 = as little thinking as possible
     */
    static Optional<ThinkingConfig> forModel(String model, ThinkingLevel.Known level, Integer budget) {
        boolean hasBudget = budget != null && budget >= 0;
        if (model.startsWith(GEMINI_3_PREFIX)) {
            if (level != null) {
                return Optional.of(ThinkingConfig.builder().thinkingLevel(level).build());
            }
            return hasBudget && budget == 0
                    ? Optional.of(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.MINIMAL).build())
                    : Optional.empty();
        }
        if (model.startsWith(GEMINI_2_5_PREFIX) && hasBudget) {
            return Optional.of(ThinkingConfig.builder().thinkingBudget(budget).build());
        }
        return Optional.empty();
    }
}
