package com.jobbeacon.controller;

import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.security.GatewayHeaders;
import com.jobbeacon.service.ChatbotService;
import com.jobbeacon.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final UserService userService;

    @PostMapping
    public ResponseGeneral<ChatResponse> chat(@RequestHeader(GatewayHeaders.USER_ID) String uid, @Valid @RequestBody ChatRequest request) {
        return chatbotService.chat(userService.getUserId(uid), request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestHeader(GatewayHeaders.USER_ID) String uid, @Valid @RequestBody ChatRequest request,
                                             Locale locale) {
        SseEmitter emitter = chatbotService.stream(userService.getUserId(uid), request.message(), locale);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(X_ACCEL_BUFFERING, "no")
                .body(emitter);
    }

    @DeleteMapping
    public ResponseGeneral<Void> clearSession(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
        chatbotService.clearSession(userService.getUserId(uid));
        return ResponseGeneral.ofSuccess("success");
    }
}
