package com.jobbeacon.llm.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LlmToolArgs {

    static final int COMPACT_KEEP_MAX_CHARS = 500;
    static final String COMPACT_NOTE = "Kết quả đã được rút gọn; gọi lại tool nếu cần";

    private static final String DEFAULT_CURRENCY = "VND";
    private static final ObjectMapper SIZE_MAPPER = new ObjectMapper();
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private LlmToolArgs() {
    }

    static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static Long longValue(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = string(args, key);
        if (text == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
    }

    static int intValue(Map<String, Object> args, String key, int defaultValue) {
        Long value = longValue(args, key);
        return value == null ? defaultValue : value.intValue();
    }

    static List<String> strings(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return List.of();
        }
        Collection<?> items = value instanceof Collection<?> collection
                ? collection
                : List.of(String.valueOf(value).split(","));
        return items.stream()
                .filter(item -> item != null)
                .map(item -> String.valueOf(item).trim())
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    static UUID uuid(Map<String, Object> args, String key) {
        String text = string(args, key);
        if (text == null) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("'" + key + "' must be a UUID");
        }
    }

    static Map<String, Object> compactBySize(Object result) {
        if (result instanceof Map<?, ?> && jsonLength(result) < COMPACT_KEEP_MAX_CHARS) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            return map;
        }
        return Map.of("note", COMPACT_NOTE);
    }

    private static int jsonLength(Object value) {
        try {
            return SIZE_MAPPER.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            return Integer.MAX_VALUE;
        }
    }

    static String date(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, LOCAL_ZONE).toString();
    }

    static String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String collapsed = text.replaceAll("\\s+", " ").strip();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() <= maxChars ? collapsed : collapsed.substring(0, maxChars).stripTrailing() + "...";
    }

    static String id(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    static void putCurrencyIfNotDefault(Map<String, Object> map, String currency) {
        if (currency != null && !currency.isBlank() && !DEFAULT_CURRENCY.equalsIgnoreCase(currency.trim())) {
            map.put("currency", currency);
        }
    }
}
