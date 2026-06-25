package com.tradepulse.order.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.order.dto.request.PlaceOrderRequest;
import com.tradepulse.order.dto.response.OrderResponse;
import com.tradepulse.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlaceOrderRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.placeOrder(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> listOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(orderService.listOrders(userId, page, size)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(orderId, userId)));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancelOrder(orderId, userId)));
    }
}
