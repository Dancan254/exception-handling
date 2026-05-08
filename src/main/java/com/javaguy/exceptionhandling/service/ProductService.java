package com.javaguy.exceptionhandling.service;

import com.javaguy.exceptionhandling.domain.entity.Product;
import com.javaguy.exceptionhandling.domain.repository.ProductRepository;
import com.javaguy.exceptionhandling.dto.request.ProductRequest;
import com.javaguy.exceptionhandling.dto.response.ProductResponse;
import com.javaguy.exceptionhandling.exception.BusinessRuleException;
import com.javaguy.exceptionhandling.exception.ConflictException;
import com.javaguy.exceptionhandling.exception.ErrorCode;
import com.javaguy.exceptionhandling.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for product catalogue and inventory management.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException(
                "A product with SKU '" + request.sku() + "' already exists",
                ErrorCode.PRODUCT_SKU_CONFLICT
            );
        }
        Product product = Product.builder()
            .sku(request.sku())
            .name(request.name())
            .price(request.price())
            .stock(request.stock())
            .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
            .map(ProductResponse::from)
            .toList();
    }

    /**
     * Decrements stock by {@code quantity} within the caller's transaction.
     * Must be called inside an active transaction — no stock reduction is committed
     * unless the surrounding unit of work succeeds.
     *
     * @throws BusinessRuleException if available stock is less than {@code quantity}
     */
    public void reduceStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new BusinessRuleException(
                String.format(
                    "Insufficient stock for product '%s'. Available: %d, Requested: %d",
                    product.getName(), product.getStock(), quantity
                ),
                ErrorCode.PRODUCT_INSUFFICIENT_STOCK
            );
        }
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    /**
     * Loads a {@link Product} entity by id or throws {@link ResourceNotFoundException}.
     */
    @Transactional(readOnly = true)
    public Product getOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
