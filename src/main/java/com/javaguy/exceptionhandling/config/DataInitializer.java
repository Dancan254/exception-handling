package com.javaguy.exceptionhandling.config;

import com.javaguy.exceptionhandling.domain.entity.Product;
import com.javaguy.exceptionhandling.domain.entity.User;
import com.javaguy.exceptionhandling.domain.repository.ProductRepository;
import com.javaguy.exceptionhandling.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds the in-memory H2 database with representative data on startup.
 *
 * <p>Provides two users (one active, one inactive) and two products
 * (one in stock, one out of stock) so that every exception scenario in the
 * API can be exercised without requiring manual setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        userRepository.save(User.builder().name("Alice Johnson").email("alice@example.com").build());
        userRepository.save(User.builder().name("Bob Smith").email("bob@example.com").active(false).build());

        productRepository.save(Product.builder()
            .sku("LAPTOP-001").name("Pro Laptop 15").price(new BigDecimal("1299.99")).stock(10).build());
        productRepository.save(Product.builder()
            .sku("PHONE-001").name("SmartPhone X").price(new BigDecimal("799.99")).stock(0).build());

        log.info("Seeded {} users and {} products", userRepository.count(), productRepository.count());
    }
}
