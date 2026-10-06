package com.jobbeacon.exception;

public class GeminiEmptyResponseException extends RuntimeException {

    public GeminiEmptyResponseException(String message) {
        super(message);
    }
}
