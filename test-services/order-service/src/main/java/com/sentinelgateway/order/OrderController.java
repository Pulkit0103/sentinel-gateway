package com.sentinelgateway.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    @GetMapping
    ResponseEntity<ServiceResponse<List<Map<String, String>>>> listOrders(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.ok(new ServiceResponse<>(
                List.of(
                        Map.of("id", "order-1", "item", "Widget", "status", "PENDING"),
                        Map.of("id", "order-2", "item", "Gadget", "status", "SHIPPED")),
                "order-service", requestId, Instant.now().toString()));
    }

    @GetMapping("/{id}")
    ResponseEntity<ServiceResponse<Map<String, String>>> getOrder(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.ok(new ServiceResponse<>(
                Map.of("id", id, "item", "Widget", "status", "PENDING"),
                "order-service", requestId, Instant.now().toString()));
    }

    @PostMapping
    ResponseEntity<ServiceResponse<Map<String, String>>> createOrder(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.status(201).body(new ServiceResponse<>(
                Map.of("id", "order-new", "item", body.getOrDefault("item", "unknown"), "status", "PENDING"),
                "order-service", requestId, Instant.now().toString()));
    }

    record ServiceResponse<T>(T data, String service, String requestId, String timestamp) {}
}
