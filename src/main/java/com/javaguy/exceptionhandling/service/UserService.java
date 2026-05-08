package com.javaguy.exceptionhandling.service;

import com.javaguy.exceptionhandling.domain.entity.User;
import com.javaguy.exceptionhandling.domain.repository.UserRepository;
import com.javaguy.exceptionhandling.dto.request.UserRequest;
import com.javaguy.exceptionhandling.dto.response.UserResponse;
import com.javaguy.exceptionhandling.exception.BusinessRuleException;
import com.javaguy.exceptionhandling.exception.ConflictException;
import com.javaguy.exceptionhandling.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for user management.
 *
 * <p>Uniqueness is checked before the insert so that the application produces a
 * meaningful {@link ConflictException} rather than letting a database constraint
 * violation bubble up as a raw {@link org.springframework.dao.DataIntegrityViolationException}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Creates a new user, enforcing email uniqueness at the service layer.
     */
    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                "A user with email '" + request.email() + "' already exists"
            );
        }
        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
            .map(UserResponse::from)
            .toList();
    }

    /**
     * Soft-deactivates a user. Idempotency is intentionally not provided here:
     * attempting to deactivate an already-inactive user is treated as a business rule
     * violation so callers get feedback rather than a silent no-op.
     */
    @Transactional
    public UserResponse deactivate(Long id) {
        User user = getOrThrow(id);
        if (!user.isActive()) {
            throw new BusinessRuleException(
                "User with id '" + id + "' is already inactive"
            );
        }
        user.setActive(false);
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Loads a {@link User} entity by id or throws {@link ResourceNotFoundException}.
     * Package-accessible so that services depending on a user entity (e.g., {@link OrderService})
     * reuse this lookup and its exception contract rather than duplicating repository calls.
     */
    @Transactional(readOnly = true)
    public User getOrThrow(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
