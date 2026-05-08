package com.javaguy.exceptionhandling.domain.entity;

/**
 * Lifecycle states of an {@link Order}.
 *
 * <p>Valid cancellation window: {@code PENDING} and {@code CONFIRMED} only.
 * Attempting to cancel a {@code SHIPPED}, {@code DELIVERED}, or {@code CANCELLED}
 * order raises a {@link com.javaguy.exceptionhandling.exception.BusinessRuleException}.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
