package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a resource previously existed but has been permanently removed.
 * Maps to HTTP 410 Gone — semantically stronger than 404 because it signals to
 * clients and crawlers that they should not retry the request.
 *
 * <p><strong>Demonstrating the {@link ResponseStatus} gotcha:</strong><br>
 * {@code @ResponseStatus(HttpStatus.GONE)} instructs Spring's
 * {@code DefaultHandlerExceptionResolver} to set the response status if this exception
 * propagates to the servlet layer without being caught by any {@code @ExceptionHandler}.
 * In this project the {@code GlobalExceptionHandler} has a catch-all
 * {@code @ExceptionHandler(AppException.class)} which intercepts this exception first,
 * so {@link ResponseStatus} here is never applied by the framework — the handler decides
 * the status by reading {@link AppException#getStatus()}. The annotation is retained as
 * documentation of intent and as a safety net if the global handler is ever removed.
 */
@ResponseStatus(HttpStatus.GONE)
public class ResourceGoneException extends AppException {

    /**
     * @param resource the type of resource that no longer exists (e.g., "User")
     * @param id       the identifier that was requested
     */
    public ResourceGoneException(String resource, Object id) {
        super(
            String.format("%s with id '%s' has been permanently removed and will not return", resource, id),
            ErrorCode.RESOURCE_GONE,
            HttpStatus.GONE
        );
    }
}
