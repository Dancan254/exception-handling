package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation violates a uniqueness constraint, such as registering
 * a user with an email address that already exists. Maps to HTTP 409 Conflict.
 *
 * <p>Prefer throwing this at the service layer (after an explicit existence check)
 * rather than letting a {@link org.springframework.dao.DataIntegrityViolationException}
 * surface from JPA. Service-layer checks produce clearer messages and avoid exposing
 * database-specific constraint names in error responses.
 */
public class ConflictException extends AppException {

    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
