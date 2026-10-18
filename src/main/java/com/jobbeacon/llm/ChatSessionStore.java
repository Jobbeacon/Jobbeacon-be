package com.jobbeacon.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.genai.JsonSerializable;
import com.google.genai.types.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ChatSessionStore {

    private static final String KEY_PREFIX = "chat:session:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final int maxHistory;

    public ChatSessionStore(StringRedisTemplate redis,
                            @Value("${app.chat.session-ttl:30m}") Duration ttl,
                            @Value("${app.chat.max-history:40}") int maxHistory) {
        this.redis = redis;
        this.ttl = ttl;
        this.maxHistory = maxHistory;
    }

    public List<Content> load(UUID userId) {
        String json = redis.opsForValue().get(key(userId));
        if (json == null) {
            return new ArrayList<>();
        }
        List<Content> contents = new ArrayList<>();
        try {
            for (JsonNode node : JsonSerializable.stringToJsonNode(json)) {
                contents.add(JsonSerializable.fromJsonNode(node, Content.class));
            }
        } catch (RuntimeException exception) {
            log.warn("[chat] Drop unreadable session of user {}", userId, exception);
            delete(userId);
            return new ArrayList<>();
        }
        return contents;
    }

    public void save(UUID userId, List<Content> contents) {
        ArrayNode array = JsonSerializable.objectMapper().createArrayNode();
        for (Content content : trim(contents)) {
            array.add(JsonSerializable.toJsonNode(content));
        }
        redis.opsForValue().set(key(userId), array.toString(), ttl);
    }

    public void delete(UUID userId) {
        redis.delete(key(userId));
    }

    private List<Content> trim(List<Content> contents) {
        if (contents.size() <= maxHistory) {
            return contents;
        }
        int start = contents.size() - maxHistory;
        while (start < contents.size() && !isUserText(contents.get(start))) {
            start++;
        }
        return contents.subList(start, contents.size());
    }

    private boolean isUserText(Content content) {
        return "user".equals(content.role().orElse(null))
                && content.parts().orElse(List.of()).stream()
                .anyMatch(part -> part.text().isPresent() && part.functionResponse().isEmpty());
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
