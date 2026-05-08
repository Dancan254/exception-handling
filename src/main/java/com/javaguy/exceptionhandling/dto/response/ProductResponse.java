package com.javaguy.exceptionhandling.dto.response;

import com.javaguy.exceptionhandling.domain.entity.Product;

import java.math.BigDecimal;

/**
 * Outbound representation of a {@link Product}.
 */
public record ProductResponse(Long id, String sku, String name, BigDecimal price, int stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getPrice(),
            product.getStock()
        );
    }
}
