package com.javaguy.exceptionhandling.controller;

import com.javaguy.exceptionhandling.dto.request.ProductRequest;
import com.javaguy.exceptionhandling.dto.response.ProductResponse;
import com.javaguy.exceptionhandling.exception.ExternalServiceException;
import com.javaguy.exceptionhandling.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the product catalogue.
 *
 * <p>The {@code /price-check} endpoint demonstrates {@link ExternalServiceException}
 * and proper exception chaining: the original low-level cause is wrapped and preserved
 * for observability while a user-friendly message is surfaced in the response.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    /**
     * Simulates a call to an external pricing service to demonstrate
     * {@link ExternalServiceException} and exception chaining.
     *
     * <p>The original {@link RuntimeException} from the integration layer is always
     * passed as the cause so that the full chain appears in logs. Only the wrapper
     * message is sent to the client via the global handler.
     */
    @GetMapping("/{id}/price-check")
    public ResponseEntity<Map<String, Object>> externalPriceCheck(@PathVariable Long id) {
        ProductResponse product = productService.findById(id);
        try {
            simulateExternalPricingCall(product.sku());
        } catch (RuntimeException e) {
            throw new ExternalServiceException("PricingService", "Failed to retrieve live pricing data", e);
        }
        return ResponseEntity.ok(Map.of("sku", product.sku(), "listedPrice", product.price()));
    }

    private void simulateExternalPricingCall(String sku) {
        throw new RuntimeException("Connection to pricing-service timed out after 3000ms");
    }
}
