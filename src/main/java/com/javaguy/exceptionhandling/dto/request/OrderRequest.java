package com.javaguy.exceptionhandling.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound payload for placing a new order.
 */
public record OrderRequest(

        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Product ID is required")
        Long productId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {}
