package com.jobbeacon.llm;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.jobbeacon.llm.tool.LlmToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces full tool results of the turn that just finished with the per-tool summaries (C2),
 * so later turns only carry the function call (name + args) and a short result.
 */
@Component
@RequiredArgsConstructor
public class ChatHistoryCompactor {

    private final LlmToolRegistry registry;

    /**
     * Returns a new list where every {@code functionResponse} part at or after {@code turnStartIndex}
     * is rebuilt with {@link LlmToolRegistry#compact}. Every other part (text, functionCall,
     * thoughtSignature, ...) and every content before {@code turnStartIndex} is kept unchanged.
     */
    public List<Content> compact(List<Content> contents, int turnStartIndex) {
        List<Content> result = new ArrayList<>(contents.size());
        int start = Math.max(0, turnStartIndex);
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            result.add(i < start ? content : compactContent(content));
        }
        return result;
    }

    private Content compactContent(Content content) {
        List<Part> parts = content.parts().orElse(null);
        if (parts == null || parts.stream().noneMatch(part -> part.functionResponse().isPresent())) {
            return content;
        }
        List<Part> compacted = new ArrayList<>(parts.size());
        for (Part part : parts) {
            compacted.add(part.functionResponse().map(response -> compactPart(part, response)).orElse(part));
        }
        return content.toBuilder().parts(compacted).build();
    }

    private Part compactPart(Part part, FunctionResponse functionResponse) {
        String name = functionResponse.name().orElse(null);
        Map<String, Object> response = functionResponse.response().orElse(null);
        if (name == null || response == null) {
            return part;
        }
        Map<String, Object> compacted = registry.compact(name, response);
        if (compacted == null || compacted == response) {
            return part;
        }
        return part.toBuilder()
                .functionResponse(functionResponse.toBuilder().response(compacted).build())
                .build();
    }
}
