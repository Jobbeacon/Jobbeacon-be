package com.jobbeacon.llm;

import java.util.Map;

/**
 * Receives the progress of one chat turn. All callbacks run on the thread that runs the turn.
 * <ul>
 *     <li>{@link #onToolCall} is called right before a tool runs.</li>
 *     <li>{@link #onDelta} receives the reply text as the model streams it (also the fallback reply).</li>
 *     <li>{@link #isCancelled} is checked after every chunk, before every round and tool, and before saving.
 *     Once it returns {@code true} the turn closes the stream, releases the lock, saves nothing and
 *     throws {@link ChatTurnCancelledException}.</li>
 * </ul>
 * An exception thrown by a callback while {@link #isCancelled} is {@code true} is treated as a cancel.
 */
public interface ChatTurnListener {

    ChatTurnListener NONE = new ChatTurnListener() {
    };

    default void onToolCall(String name, Map<String, Object> args) {
    }

    default void onDelta(String text) {
    }

    default boolean isCancelled() {
        return false;
    }

    /** Ends a cancelled turn. Not an error: callers stop silently and send nothing to the client. */
    final class ChatTurnCancelledException extends RuntimeException {

        public ChatTurnCancelledException() {
            super("Chat turn cancelled", null, false, false);
        }
    }
}
