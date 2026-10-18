package com.jobbeacon.dto.response;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String reply,
        List<ToolCall> toolCalls
) {
    public record ToolCall(String name, Map<String, Object> args, boolean success) {
    }
}
