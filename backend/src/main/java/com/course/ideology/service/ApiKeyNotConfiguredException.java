package com.course.ideology.service;

public class ApiKeyNotConfiguredException extends RuntimeException {
    public ApiKeyNotConfiguredException(String message) {
        super(message);
    }
}
