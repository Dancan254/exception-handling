package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is structurally valid but violates a domain business rule.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * <p>Examples: placing an order for an inactive user, cancelling a shipped order,
 * or requesting more stock than is available. These are not input validation errors
 * (which are handled by Bean Validation) — they are semantic failures that depend on
 * the current state of the system.
 */
public class BusinessRuleException extends AppException {

    public BusinessRuleException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
