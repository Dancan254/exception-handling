package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a call to a downstream external service fails unexpectedly.
 * Maps to HTTP 502 Bad Gateway.
 *
 * <p>The constructor always requires the original {@code cause} to enforce exception
 * chaining. Losing the cause would hide the true failure (e.g., a connection timeout
 * or deserialization error) from logs, making incidents significantly harder to debug.
 */
public class ExternalServiceException extends AppException {

    /**
     * @param serviceName the logical name of the failing downstream service
     * @param detail      a short, human-readable description of the failure
     * @param cause       the underlying exception — must not be {@code null}
     */
    public ExternalServiceException(String serviceName, String detail, Throwable cause) {
        super(
            String.format("External service '%s' failed: %s", serviceName, detail),
            ErrorCode.EXTERNAL_SERVICE_ERROR,
            HttpStatus.BAD_GATEWAY,
            cause
        );
    }
}
