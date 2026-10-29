package com.jobbeacon.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class ChatSessionLock {

    private static final String KEY_PREFIX = "chat:lock:";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public ChatSessionLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> tryAcquire(UUID userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key(userId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void release(UUID userId, String token) {
        if (token == null) {
            return;
        }
        try {
            Long deleted = redis.execute(RELEASE_SCRIPT, List.of(key(userId)), token);
            if (deleted == null || deleted == 0L) {
                log.warn("[chat] Lock of user {} already expired or taken over", userId);
            }
        } catch (RuntimeException exception) {
            log.warn("[chat] Failed to release lock of user {}", userId, exception);
        }
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
