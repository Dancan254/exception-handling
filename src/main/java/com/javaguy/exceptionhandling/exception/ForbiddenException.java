package com.javaguy.exceptionhandling.exception;

import com.javaguy.exceptionhandling.exception.base.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated principal attempts an action they are not authorized
 * to perform. Maps to HTTP 403 Forbidden.
 *
 * <p>In a full Spring Security setup this role is typically filled by
 * {@code AccessDeniedException}, which Spring Security resolves to 403 automatically.
 * This class covers the same semantic for non-security authorization checks
 * (e.g., a user attempting to modify another user's resource) without a Security dependency.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }
}
