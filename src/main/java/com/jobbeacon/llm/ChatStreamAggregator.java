package com.jobbeacon.llm;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Merges the chunks of one Gemini streaming round into a single {@code model} {@link Content}.
 * <ul>
 *     <li>Non-thought text is passed to {@code onText} as it arrives.</li>
 *     <li>Consecutive plain text parts are joined into one part.</li>
 *     <li>Parts carrying a {@code thoughtSignature}, {@code functionCall} and other non-text parts are kept as-is.</li>
 *     <li>Thought parts without a signature and parts with no text, signature or function call are dropped.</li>
 * </ul>
 * One instance per round; not thread-safe.
 */
public class ChatStreamAggregator {

    private static final String MODEL_ROLE = "model";

    private final Consumer<String> onText;

    private final List<Part> parts = new ArrayList<>();

    private final List<FunctionCall> functionCalls = new ArrayList<>();

    private final StringBuilder pendingText = new StringBuilder();

    private final StringBuilder fullText = new StringBuilder();

    private boolean emittedText;

    private int chunkCount;

    private FinishReason finishReason;

    private GenerateContentResponseUsageMetadata usageMetadata;

    private GenerateContentResponsePromptFeedback promptFeedback;

    public ChatStreamAggregator(Consumer<String> onText) {
        this.onText = Objects.requireNonNull(onText, "onText");
    }

    /**
     * Adds one chunk. Exceptions thrown by {@code onText} are propagated; {@link #hasEmittedText()} is already
     * {@code true} at that point.
     */
    public void accept(GenerateContentResponse chunk) {
        if (chunk == null) {
            return;
        }
        chunkCount++;
        chunk.usageMetadata().ifPresent(usage -> usageMetadata = usage);
        chunk.promptFeedback().ifPresent(feedback -> promptFeedback = feedback);

        Optional<Candidate> candidate = chunk.candidates()
                .filter(candidates -> !candidates.isEmpty())
                .map(candidates -> candidates.get(0));
        if (candidate.isEmpty()) {
            return;
        }
        candidate.get().finishReason().ifPresent(reason -> finishReason = reason);
        candidate.get().content()
                .flatMap(Content::parts)
                .ifPresent(chunkParts -> chunkParts.forEach(this::acceptPart));
    }

    private void acceptPart(Part part) {
        boolean thought = part.thought().orElse(false);
        boolean hasSignature = part.thoughtSignature().isPresent();
        String text = part.text().orElse(null);

        if (thought) {
            if (hasSignature) {
                flushPendingText();
                parts.add(part);
            }
            return;
        }

        if (text != null && !text.isEmpty()) {
            fullText.append(text);
            emittedText = true;
            onText.accept(text);
        }
        part.functionCall().ifPresent(functionCalls::add);

        boolean hasFunctionCall = part.functionCall().isPresent();
        if (text == null && !hasSignature && !hasFunctionCall) {
            return;
        }
        boolean plainText = text != null && !hasSignature && !hasFunctionCall;
        if (plainText) {
            pendingText.append(text);
            return;
        }
        flushPendingText();
        parts.add(part);
    }

    private void flushPendingText() {
        if (!pendingText.isEmpty()) {
            parts.add(Part.fromText(pendingText.toString()));
            pendingText.setLength(0);
        }
    }

    /** Merged model content, or empty when the stream produced no usable part. */
    public Optional<Content> content() {
        flushPendingText();
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Content.builder()
                .role(MODEL_ROLE)
                .parts(List.copyOf(parts))
                .build());
    }

    public boolean hasEmittedText() {
        return emittedText;
    }

    public String text() {
        return fullText.toString();
    }

    public List<FunctionCall> functionCalls() {
        return List.copyOf(functionCalls);
    }

    public Optional<FinishReason> finishReason() {
        return Optional.ofNullable(finishReason);
    }

    public Optional<GenerateContentResponseUsageMetadata> usageMetadata() {
        return Optional.ofNullable(usageMetadata);
    }

    public Optional<GenerateContentResponsePromptFeedback> promptFeedback() {
        return Optional.ofNullable(promptFeedback);
    }

    public int chunkCount() {
        return chunkCount;
    }
}
