package com.javaguy.exceptionhandling.controller;

import com.javaguy.exceptionhandling.dto.request.UserRequest;
import com.javaguy.exceptionhandling.dto.response.UserResponse;
import com.javaguy.exceptionhandling.exception.ForbiddenException;
import com.javaguy.exceptionhandling.exception.ResourceGoneException;
import com.javaguy.exceptionhandling.exception.ResourceNotFoundException;
import com.javaguy.exceptionhandling.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST endpoints for user management.
 *
 * <p>Beyond standard CRUD, this controller demonstrates three exception patterns side by side:
 * <ul>
 *   <li>{@link ForbiddenException} — domain exception for authorization failures</li>
 *   <li>{@link ResourceGoneException} with {@code @ResponseStatus} — for permanently removed resources</li>
 *   <li>{@link ResponseStatusException} inline — for simple, one-off status mappings that do not
 *       warrant a dedicated exception class</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @PatchMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable Long id) {
        return userService.deactivate(id);
    }

    /**
     * Demonstrates {@link ForbiddenException}. In a real application the {@code admin}
     * flag would be derived from the authenticated principal, not a query parameter.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean admin) {
        if (!admin) {
            throw new ForbiddenException("Only administrators can delete users.");
        }
    }

    /**
     * Demonstrates {@link ResourceGoneException} on a retired endpoint.
     * HTTP 410 tells clients and crawlers this path will never return data,
     * distinct from 404 which could be temporary.
     */
    @GetMapping("/legacy/{id}")
    public UserResponse legacyFind(@PathVariable Long id) {
        throw new ResourceGoneException("User (legacy endpoint)", id);
    }

    /**
     * Demonstrates inline {@link ResponseStatusException} — appropriate for simple,
     * context-specific validation that does not recur elsewhere in the codebase.
     * For anything thrown from more than one place, create a dedicated exception class.
     */
    @GetMapping("/search")
    public UserResponse search(@RequestParam(required = false) String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'email' is required.");
        }
        return userService.findAll().stream()
            .filter(u -> u.email().equalsIgnoreCase(email))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
