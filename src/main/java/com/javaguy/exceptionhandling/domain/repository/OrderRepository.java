package com.javaguy.exceptionhandling.domain.repository;

import com.javaguy.exceptionhandling.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
