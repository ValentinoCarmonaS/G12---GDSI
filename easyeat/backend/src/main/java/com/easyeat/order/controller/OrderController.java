package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderCreatedResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        OrderCreatedResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderDetailResponse> getOrderById(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        OrderDetailResponse response = orderService.getOrderById(orderId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderSummaryResponse> cancelOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        OrderSummaryResponse response = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    private UUID resolveUserId(User user, Authentication authentication) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User u) {
                return u.getId();
            }
            if (principal instanceof UUID uid) {
                return uid;
            }
            if (principal instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new IllegalStateException("Usuario no autenticado");
    }
}
