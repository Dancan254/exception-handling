package com.javaguy.exceptionhandling.exception;

public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String serviceName, String detail, Throwable cause) {
        super(String.format("External service '%s' failed: %s", serviceName, detail), cause);
    }
}
