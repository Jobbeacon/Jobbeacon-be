package com.jobbeacon.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.genai.JsonSerializable;
import com.google.genai.types.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

@Slf4j
@Component
public class ChatSessionStore {

    private static final String KEY_PREFIX = "chat:session:";

    private static final String GENERATION_KEY_PREFIX = "chat:gen:";

    /** Generation of a session that was never cleared (no generation key). */
    private static final String INITIAL_GENERATION = "0";

    /**
     * KEYS[1] session, KEYS[2] generation; ARGV[1] expected generation, ARGV[2] value, ARGV[3] session TTL ms,
     * ARGV[4] generation TTL ms. Stores the session only while the generation still matches (missing = "0").
     */
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>(
            "local gen = redis.call('get', KEYS[2]) "
                    + "if not gen then gen = '" + INITIAL_GENERATION + "' end "
                    + "if gen ~= ARGV[1] then return 0 end "
                    + "redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
                    + "redis.call('pexpire', KEYS[2], ARGV[4]) "
                    + "return 1",
            Long.class);

    /** KEYS[1] session, KEYS[2] generation; ARGV[1] generation TTL ms. Drops the session and bumps the generation. */
    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('del', KEYS[1]) "
                    + "local gen = redis.call('incr', KEYS[2]) "
                    + "redis.call('pexpire', KEYS[2], ARGV[1]) "
                    + "return gen",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration generationTtl;
    private final int historyTokenBudget;
    private final double charsPerToken;
    private final int minHistoryTurns;

    public ChatSessionStore(StringRedisTemplate redis,
                            @Value("${app.chat.session-ttl:30m}") Duration ttl,
                            @Value("${app.chat.history-token-budget:6000}") int historyTokenBudget,
                            @Value("${app.chat.chars-per-token:3.0}") double charsPerToken,
                            @Value("${app.chat.min-history-turns:2}") int minHistoryTurns) {
        this.redis = redis;
        this.ttl = ttl;
        this.generationTtl = ttl.multipliedBy(2);
        this.historyTokenBudget = historyTokenBudget;
        this.charsPerToken = charsPerToken > 0 ? charsPerToken : 3.0;
        this.minHistoryTurns = Math.max(1, minHistoryTurns);
    }

    /**
     * Current generation of the user's session; read it before {@link #load} and pass it to {@link #save}.
     */
    public String generation(UUID userId) {
        String generation = redis.opsForValue().get(generationKey(userId));
        return generation != null ? generation : INITIAL_GENERATION;
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
            redis.delete(key(userId));
            return new ArrayList<>();
        }
        return contents;
    }

    /**
     * Trims the history to the token budget and stores it, unless the session was cleared since
     * {@code generation} was read.
     *
     * @return estimated token count of the history stored, or empty when the generation no longer matches
     */
    public OptionalInt save(UUID userId, String generation, List<Content> contents) {
        List<JsonNode> nodes = contents.stream().map(JsonSerializable::toJsonNode).toList();
        int[] tokens = nodes.stream().mapToInt(node -> estimateTokens(node.toString().length())).toArray();
        int start = trimStart(contents, tokens);

        ArrayNode array = JsonSerializable.objectMapper().createArrayNode();
        int total = 0;
        for (int i = start; i < nodes.size(); i++) {
            array.add(nodes.get(i));
            total += tokens[i];
        }
        Long saved = redis.execute(SAVE_SCRIPT, List.of(key(userId), generationKey(userId)),
                generation, array.toString(), String.valueOf(ttl.toMillis()),
                String.valueOf(generationTtl.toMillis()));
        return saved != null && saved == 1L ? OptionalInt.of(total) : OptionalInt.empty();
    }

    /** Drops the session and bumps its generation, so a turn still running cannot store the old history. */
    public void delete(UUID userId) {
        redis.execute(DELETE_SCRIPT, List.of(key(userId), generationKey(userId)),
                String.valueOf(generationTtl.toMillis()));
    }

    /** Estimated token count of the given contents: JSON length / {@code app.chat.chars-per-token}. */
    public int estimateTokens(List<Content> contents) {
        int total = 0;
        for (Content content : contents) {
            total += estimateTokens(JsonSerializable.toJsonNode(content).toString().length());
        }
        return total;
    }

    /**
     * Index of the first content to keep. The kept history always starts at a user text message,
     * keeps at least {@code minHistoryTurns} turns and otherwise drops the oldest turns until it fits
     * the token budget.
     */
    private int trimStart(List<Content> contents, int[] tokens) {
        List<Integer> turnStarts = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            if (isUserText(contents.get(i))) {
                turnStarts.add(i);
            }
        }
        if (turnStarts.isEmpty()) {
            return contents.size();
        }
        int latestAllowed = turnStarts.get(Math.max(0, turnStarts.size() - minHistoryTurns));
        int suffix = 0;
        int[] suffixTokens = new int[contents.size() + 1];
        for (int i = contents.size() - 1; i >= 0; i--) {
            suffix += tokens[i];
            suffixTokens[i] = suffix;
        }
        for (int start : turnStarts) {
            if (start >= latestAllowed || suffixTokens[start] <= historyTokenBudget) {
                return start;
            }
        }
        return latestAllowed;
    }

    private int estimateTokens(int chars) {
        return (int) Math.ceil(chars / charsPerToken);
    }

    private boolean isUserText(Content content) {
        return "user".equals(content.role().orElse(null))
                && content.parts().orElse(List.of()).stream()
                .anyMatch(part -> part.text().isPresent() && part.functionResponse().isEmpty());
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    private String generationKey(UUID userId) {
        return GENERATION_KEY_PREFIX + userId;
    }
}
