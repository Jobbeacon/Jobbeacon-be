package com.jobbeacon.llm.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import com.jobbeacon.exception.base.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LlmToolRegistry {

    private final Map<String, LlmTool> tools;
    private final ObjectMapper objectMapper;

    public LlmToolRegistry(List<LlmTool> tools, ObjectMapper objectMapper) {
        this.tools = tools.stream()
                .collect(Collectors.toMap(LlmTool::name, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Duplicate LLM tool name: " + a.name());
                }, LinkedHashMap::new));
        this.objectMapper = objectMapper;
    }

    public Tool tool() {
        return Tool.builder()
                .functionDeclarations(tools.values().stream().map(LlmTool::declaration).toList())
                .build();
    }

    public Map<String, Object> run(String name, Map<String, Object> args) {
        LlmTool tool = tools.get(name);
        if (tool == null) {
            return error("Unknown tool: " + name);
        }
        log.info("[LLM tool] {} args={}", name, args);
        try {
            Object result = tool.execute(args);
            return Map.of("result", objectMapper.convertValue(result, Object.class));
        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        } catch (BaseException exception) {
            log.warn("[LLM tool] {} failed: {}", name, exception.getMessage());
            String code = exception.getCode();
            return error(code == null || code.isBlank() ? exception.getMessage() : code);
        } catch (RuntimeException exception) {
            log.error("[LLM tool] {} failed", name, exception);
            return error("Tool execution failed");
        }
    }

    private Map<String, Object> error(String message) {
        return Map.of("error", message == null ? "Unknown error" : message);
    }
}
