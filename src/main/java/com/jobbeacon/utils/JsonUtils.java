package com.jobbeacon.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static String toPrettyJson(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(object);
    }

    public static <T> T convert(Object object, Class<T> targetType) {
        return OBJECT_MAPPER.convertValue(object, targetType);
    }

    public static <T> T fromJson(String json, Class<T> targetType)
            throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(json, targetType);
    }
}