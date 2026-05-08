package com.javaguy.exceptionhandling.controller;

import com.javaguy.exceptionhandling.dto.request.OrderRequest;
import com.javaguy.exceptionhandling.dto.response.OrderResponse;
import com.javaguy.exceptionhandling.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for order lifecycle management.
 * Error scenarios are fully driven by {@link OrderService} business rules and
 * surfaced through {@link com.javaguy.exceptionhandling.handler.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody OrderRequest request) {
        return orderService.placeOrder(request);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }
}
