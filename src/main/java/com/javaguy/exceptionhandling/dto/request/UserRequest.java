package com.javaguy.exceptionhandling.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for creating a new user.
 * Bean Validation annotations here drive the 422/400 responses handled by
 * {@link com.javaguy.exceptionhandling.handler.GlobalExceptionHandler#handleMethodArgumentNotValid}.
 */
public record UserRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email
) {}
