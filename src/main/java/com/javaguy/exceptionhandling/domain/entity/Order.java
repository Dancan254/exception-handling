package com.javaguy.exceptionhandling.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a customer order linking a {@link User} to a {@link Product}.
 *
 * <p>The {@link OrderStatus} lifecycle is enforced at the service layer:
 * only {@code PENDING} and {@code CONFIRMED} orders may be cancelled.
 * Attempting any other transition raises a
 * {@link com.javaguy.exceptionhandling.exception.BusinessRuleException}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime placedAt;
}
