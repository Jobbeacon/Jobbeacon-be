package com.jobbeacon.llm.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String RESULT_KEY = "result";
    private static final String ERROR_KEY = "error";

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

    public Map<String, Object> run(ToolContext context, String name, Map<String, Object> args) {
        LlmTool tool = tools.get(name);
        if (tool == null) {
            return error("Unknown tool: " + name);
        }
        log.info("(run) LLM tool: {}", name);
        try {
            Object result = tool.execute(context, args);
            return Map.of(RESULT_KEY, objectMapper.convertValue(result, Object.class));
        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        } catch (BaseException exception) {
            log.warn("(run) LLM tool {} failed: {}", name, exception.getMessage());
            String code = exception.getCode();
            return error(code == null || code.isBlank() ? exception.getMessage() : code);
        } catch (RuntimeException exception) {
            log.error("(run) LLM tool {} failed", name, exception);
            return error("Tool execution failed");
        }
    }

    public Map<String, Object> compact(String name, Map<String, Object> response) {
        LlmTool tool = tools.get(name);
        if (tool == null || response == null || response.containsKey(ERROR_KEY)) {
            return response;
        }
        Object result = response.get(RESULT_KEY);
        Map<String, Object> compacted;
        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) map;
            compacted = tool.compact(resultMap);
        } else {
            compacted = LlmToolArgs.compactBySize(result);
        }
        return Map.of(RESULT_KEY, compacted == null ? LlmToolArgs.compactBySize(result) : compacted);
    }

    private Map<String, Object> error(String message) {
        return Map.of(ERROR_KEY, message == null ? "Unknown error" : message);
    }
}
