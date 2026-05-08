package com.javaguy.exceptionhandling.service;

import com.javaguy.exceptionhandling.domain.entity.Order;
import com.javaguy.exceptionhandling.domain.entity.OrderStatus;
import com.javaguy.exceptionhandling.domain.entity.Product;
import com.javaguy.exceptionhandling.domain.entity.User;
import com.javaguy.exceptionhandling.domain.repository.OrderRepository;
import com.javaguy.exceptionhandling.dto.request.OrderRequest;
import com.javaguy.exceptionhandling.dto.response.OrderResponse;
import com.javaguy.exceptionhandling.exception.BusinessRuleException;
import com.javaguy.exceptionhandling.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Business logic for order lifecycle management.
 *
 * <p>Demonstrates multiple exception types composing naturally within a single
 * transactional operation: a {@link ResourceNotFoundException} if the user or product
 * does not exist, a {@link BusinessRuleException} if the user is inactive or stock is
 * insufficient, and the same pattern for cancellation guard logic.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
        OrderStatus.PENDING,
        OrderStatus.CONFIRMED
    );

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductService productService;

    /**
     * Places an order after validating user status and product stock.
     * The entire operation — stock reduction and order creation — runs in one transaction.
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        User user = userService.getOrThrow(request.userId());
        if (!user.isActive()) {
            throw new BusinessRuleException(
                "Inactive users cannot place orders. User id: " + user.getId()
            );
        }

        Product product = productService.getOrThrow(request.productId());
        productService.reduceStock(product, request.quantity());

        Order order = Order.builder()
            .user(user)
            .product(product)
            .quantity(request.quantity())
            .status(OrderStatus.PENDING)
            .placedAt(LocalDateTime.now())
            .build();

        return OrderResponse.from(orderRepository.save(order));
    }

    /**
     * Cancels an order that is still within the cancellable window.
     *
     * @throws BusinessRuleException if the order status does not permit cancellation
     */
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = getOrThrow(id);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessRuleException(
                String.format(
                    "Order %d cannot be cancelled in status '%s'. Only PENDING or CONFIRMED orders are cancellable.",
                    id, order.getStatus()
                )
            );
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return OrderResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
            .map(OrderResponse::from)
            .toList();
    }

    private Order getOrThrow(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }
}
