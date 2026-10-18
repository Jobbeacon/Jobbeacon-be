package com.jobbeacon.llm.tool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LlmToolArgs {

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
}
