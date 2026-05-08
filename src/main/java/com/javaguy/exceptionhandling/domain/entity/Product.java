package com.javaguy.exceptionhandling.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a purchasable product with inventory tracking.
 *
 * <p>The {@code sku} field is a business-level unique identifier. Attempting to
 * create a product with a duplicate SKU raises a
 * {@link com.javaguy.exceptionhandling.exception.ConflictException} at the service layer
 * before the insert reaches the database.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;
}
