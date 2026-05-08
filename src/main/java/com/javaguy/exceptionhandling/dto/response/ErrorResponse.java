package com.javaguy.exceptionhandling.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response body returned by {@link com.javaguy.exceptionhandling.handler.GlobalExceptionHandler}
 * for every non-2xx response.
 *
 * <p>The {@code errors} field is only included in the response when non-null — validation
 * failures populate it with a field-to-message map; all other errors leave it absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> errors
) {

    /**
     * Convenience factory for single-message errors with no field-level detail.
     */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            LocalDateTime.now(),
            null
        );
    }
}
