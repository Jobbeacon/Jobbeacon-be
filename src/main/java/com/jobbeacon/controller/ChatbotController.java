package com.jobbeacon.controller;

import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseGeneral<ChatResponse> chat(@CurrentUser User user, @Valid @RequestBody ChatRequest request) {
        return chatbotService.chat(user.getId(), request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@CurrentUser User user, @Valid @RequestBody ChatRequest request,
                                             Locale locale) {
        SseEmitter emitter = chatbotService.stream(user.getId(), request.message(), locale);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(X_ACCEL_BUFFERING, "no")
                .body(emitter);
    }

    @DeleteMapping
    public ResponseGeneral<Void> clearSession(@CurrentUser User user) {
        chatbotService.clearSession(user.getId());
        return ResponseGeneral.ofSuccess("success");
    }
}
