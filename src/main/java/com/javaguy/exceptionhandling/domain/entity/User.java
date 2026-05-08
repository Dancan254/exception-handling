package com.javaguy.exceptionhandling.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents an application user.
 *
 * <p>The {@code active} flag drives business-rule checks such as preventing
 * inactive users from placing orders. Deactivation is soft-delete: the record
 * is retained for order history integrity.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
