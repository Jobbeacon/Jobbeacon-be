package com.jobbeacon.service;

import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.llm.ChatTurnListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;
import java.util.UUID;

public interface ChatbotService {
    ResponseGeneral<ChatResponse> chat(UUID userId, ChatRequest request);

    /**
     * Runs one chat turn: takes the user lock, streams the model with tools and saves the turn.
     *
     * @throws com.jobbeacon.exception.ChatException busy (409) or unavailable (503); nothing is saved
     * @throws ChatTurnListener.ChatTurnCancelledException when the listener cancelled; nothing is saved
     */
    ChatResponse runTurn(UUID userId, String message, ChatTurnListener listener);

    /**
     * Starts one chat turn on the chat stream executor and returns the SSE emitter that receives its events.
     * Call on the request thread; {@code locale} is used for the error message.
     */
    SseEmitter stream(UUID userId, String message, Locale locale);

    void clearSession(UUID userId);
}
