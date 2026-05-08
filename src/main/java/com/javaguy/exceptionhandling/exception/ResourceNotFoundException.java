package com.javaguy.exceptionhandling.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s with %s '%s' was not found", resource, field, value));
    }
}
