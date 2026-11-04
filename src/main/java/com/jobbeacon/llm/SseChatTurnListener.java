package com.jobbeacon.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.dto.response.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends the progress of one chat turn as SSE events ({@code tool_call}, {@code delta}, {@code done},
 * {@code error}). Events are sent only from the worker thread running the turn. The emitter callbacks
 * (completion, timeout, error) only raise the cancel flag. A failed send raises the flag and ends the turn
 * with {@link ChatTurnCancelledException}.
 */
public class SseChatTurnListener implements ChatTurnListener {

    private static final String EVENT_TOOL_CALL = "tool_call";
    private static final String EVENT_DELTA = "delta";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public SseChatTurnListener(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(this::cancel);
        emitter.onError(error -> cancel());
    }

    @Override
    public void onToolCall(String name, Map<String, Object> args) {
        send(EVENT_TOOL_CALL, new ToolCallEvent(name, args));
    }

    @Override
    public void onDelta(String text) {
        send(EVENT_DELTA, new DeltaEvent(text));
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    /** Sends {@code done} and completes the emitter. Does nothing if the client is gone. */
    public void done(ChatResponse response) {
        List<ToolCallStatus> toolCalls = response.toolCalls().stream()
                .map(call -> new ToolCallStatus(call.name(), call.success()))
                .toList();
        sendQuietly(EVENT_DONE, new DoneEvent(toolCalls));
        complete();
    }

    /** Sends {@code error} and completes the emitter. Does nothing if the client is gone. */
    public void error(String message) {
        sendQuietly(EVENT_ERROR, new ErrorEvent(message));
        complete();
    }

    /** Completes the emitter; ignored when it is already completed. */
    public void complete() {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // already completed by the container (timeout / disconnect)
        }
    }

    private void sendQuietly(String event, Object payload) {
        try {
            send(event, payload);
        } catch (ChatTurnCancelledException ignored) {
            // client is gone, nothing left to tell it
        }
    }

    private void send(String event, Object payload) {
        if (cancelled.get()) {
            throw new ChatTurnCancelledException();
        }
        String data;
        try {
            data = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize SSE event " + event, exception);
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException exception) {
            cancel();
            throw new ChatTurnCancelledException();
        }
    }

    record ToolCallEvent(String name, Map<String, Object> args) {
    }

    record DeltaEvent(String text) {
    }

    record DoneEvent(List<ToolCallStatus> toolCalls) {
    }

    record ToolCallStatus(String name, boolean success) {
    }

    record ErrorEvent(String message) {
    }
}
