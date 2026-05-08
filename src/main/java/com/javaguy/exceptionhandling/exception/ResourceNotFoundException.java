package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist in the persistence layer.
 * Maps to HTTP 404 Not Found.
 *
 * <p>{@link ResponseStatus} is present here to illustrate its purpose: it instructs
 * Spring's {@code DefaultHandlerExceptionResolver} to use HTTP 404 when this exception
 * reaches the servlet layer unhandled. However, in any application that has a
 * {@link org.springframework.web.bind.annotation.RestControllerAdvice}, the advisor's
 * {@code @ExceptionHandler} intercepts the exception first and {@link ResponseStatus}
 * is effectively ignored. Treat it as self-documentation, not a functional directive.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends AppException {

    /**
     * @param resource the entity type that was not found (e.g., "User", "Product")
     * @param field    the lookup field (e.g., "id", "email")
     * @param value    the value that yielded no result
     */
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(
            String.format("%s with %s '%s' was not found", resource, field, value),
            ErrorCode.RESOURCE_NOT_FOUND,
            HttpStatus.NOT_FOUND
        );
    }
}
