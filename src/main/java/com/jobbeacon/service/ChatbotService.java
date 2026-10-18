package com.jobbeacon.service;

import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;

import java.util.UUID;

public interface ChatbotService {
    ResponseGeneral<ChatResponse> chat(UUID userId, ChatRequest request);

    void clearSession(UUID userId);
}
