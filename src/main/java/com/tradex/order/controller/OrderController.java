package com.tradex.order.controller;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
import com.tradex.order.service.OrderService;
import com.tradex.order.service.OrderService.CreateOrderResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResult result = orderService.createOrder(principal.getId(), request);
        HttpStatus status = result.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = result.isDuplicate() ? "Order already exists (idempotent submission)" : "Order created successfully";

        return ResponseEntity.status(status).body(ApiResponse.success(message, result.orderResponse()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<OrderResponse> orders = orderService.getUserOrders(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        OrderResponse order = orderService.getOrderById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Order details retrieved successfully", order));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        OrderResponse cancelledOrder = orderService.cancelOrder(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", cancelledOrder));
    }
}
