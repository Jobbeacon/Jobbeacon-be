package com.jobbeacon.service.impl;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.jobbeacon.client.GeminiChatClient;
import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.llm.ChatSessionStore;
import com.jobbeacon.llm.ChatbotPrompt;
import com.jobbeacon.llm.tool.LlmToolRegistry;
import com.jobbeacon.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private static final String FALLBACK_REPLY =
            "Xin lỗi, mình chưa xử lý được yêu cầu này. Bạn thử diễn đạt lại giúp mình nhé.";

    private final GeminiChatClient geminiChatClient;
    private final LlmToolRegistry toolRegistry;
    private final ChatSessionStore sessionStore;

    @Override
    public ResponseGeneral<ChatResponse> chat(UUID userId, ChatRequest request) {
        List<Content> contents = sessionStore.load(userId);
        contents.add(Content.builder().role("user").parts(Part.fromText(request.message())).build());

        String systemPrompt = ChatbotPrompt.systemPrompt();
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        for (int round = 0; ; round++) {
            GenerateContentResponse response = geminiChatClient.generateWithTools(
                    systemPrompt, contents, List.of(toolRegistry.tool()));
            List<FunctionCall> calls = response.functionCalls();
            if (calls == null || calls.isEmpty() || round == MAX_TOOL_ROUNDS) {
                String reply = reply(response);
                Content replyContent = Content.builder().role("model").parts(Part.fromText(reply)).build();
                contents.add(reply.equals(FALLBACK_REPLY) ? replyContent : modelContent(response).orElse(replyContent));
                sessionStore.save(userId, contents);
                return ResponseGeneral.ofSuccess("success", new ChatResponse(reply, toolCalls));
            }

            modelContent(response).ifPresent(contents::add);

            List<Part> results = new ArrayList<>();
            for (FunctionCall call : calls) {
                String name = call.name().orElse("");
                Map<String, Object> args = call.args().orElse(Map.of());
                Map<String, Object> result = toolRegistry.run(name, args);
                toolCalls.add(new ChatResponse.ToolCall(name, args, !result.containsKey("error")));
                results.add(Part.fromFunctionResponse(name, result));
            }
            contents.add(Content.builder().role("user").parts(results).build());
        }
    }

    @Override
    public void clearSession(UUID userId) {
        sessionStore.delete(userId);
    }

    private Optional<Content> modelContent(GenerateContentResponse response) {
        return response.candidates()
                .flatMap(candidates -> candidates.stream().findFirst())
                .flatMap(Candidate::content);
    }

    private String reply(GenerateContentResponse response) {
        String text = response.text();
        if (text == null || text.isBlank()) {
            log.warn("[chat] Gemini returned no text, finishReason={}", response.finishReason());
            return FALLBACK_REPLY;
        }
        return text.trim();
    }
}
