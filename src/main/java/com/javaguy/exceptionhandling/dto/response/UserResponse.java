package com.javaguy.exceptionhandling.dto.response;

import com.javaguy.exceptionhandling.domain.entity.User;

/**
 * Outbound representation of a {@link User}.
 * The static factory keeps the mapping logic with the DTO and avoids a separate mapper class.
 */
public record UserResponse(Long id, String name, String email, boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isActive());
    }
}
