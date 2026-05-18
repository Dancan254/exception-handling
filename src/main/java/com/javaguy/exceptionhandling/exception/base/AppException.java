package com.javaguy.exceptionhandling.exception.base;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Root of the application exception hierarchy.
 *
 * <p>Every domain-specific exception extends this class and provides its own HTTP status
 * and a machine-readable {@link #errorCode} drawn from {@link com.javaguy.exceptionhandling.exception.ErrorCode}.
 * The global exception handler reads both fields to build a consistent RFC 9457 response
 * without needing to know the concrete exception type.
 *
 * <p>The two-constructor pattern (with and without {@code cause}) enforces proper exception
 * chaining: callers that wrap a lower-level exception must always pass it as the cause so
 * that the original stack trace is preserved in logs.
 */
@Getter
public abstract class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected AppException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected AppException(String message, String errorCode, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

}
