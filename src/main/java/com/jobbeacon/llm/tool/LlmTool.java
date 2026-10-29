package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;

import java.util.Map;

public interface LlmTool {

    String name();

    FunctionDeclaration declaration();

    Object execute(Map<String, Object> args);

    default Object execute(ToolContext context, Map<String, Object> args) {
        return execute(args);
    }

    default Map<String, Object> compact(Map<String, Object> result) {
        return LlmToolArgs.compactBySize(result);
    }
}
