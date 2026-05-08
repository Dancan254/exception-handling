package com.javaguy.exceptionhandling.domain.repository;

import com.javaguy.exceptionhandling.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
