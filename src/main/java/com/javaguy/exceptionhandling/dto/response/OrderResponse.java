package com.javaguy.exceptionhandling.dto.response;

import com.javaguy.exceptionhandling.domain.entity.Order;
import com.javaguy.exceptionhandling.domain.entity.OrderStatus;

import java.time.LocalDateTime;

/**
 * Outbound representation of an {@link Order}.
 *
 * <p>Eagerly resolves {@code user} and {@code product} associations here to avoid
 * lazy-loading issues after the JPA session closes. The caller must ensure both
 * associations are loaded before constructing this record.
 */
public record OrderResponse(
        Long id,
        Long userId,
        String userName,
        Long productId,
        String productName,
        int quantity,
        OrderStatus status,
        LocalDateTime placedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getUser().getId(),
            order.getUser().getName(),
            order.getProduct().getId(),
            order.getProduct().getName(),
            order.getQuantity(),
            order.getStatus(),
            order.getPlacedAt()
        );
    }
}
