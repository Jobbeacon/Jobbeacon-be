package com.jobbeacon.llm.tool;

import com.google.genai.types.FunctionDeclaration;

import java.util.Map;

public interface LlmTool {

    String name();

    FunctionDeclaration declaration();

    Object execute(Map<String, Object> args);
}
