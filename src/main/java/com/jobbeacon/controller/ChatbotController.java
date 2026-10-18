package com.jobbeacon.controller;

import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseGeneral<ChatResponse> chat(@CurrentUser User user, @Valid @RequestBody ChatRequest request) {
        return chatbotService.chat(user.getId(), request);
    }

    @DeleteMapping
    public ResponseGeneral<Void> clearSession(@CurrentUser User user) {
        chatbotService.clearSession(user.getId());
        return ResponseGeneral.ofSuccess("success");
    }
}
