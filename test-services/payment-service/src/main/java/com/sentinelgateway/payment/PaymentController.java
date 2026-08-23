package com.sentinelgateway.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    @PostMapping
    ResponseEntity<ServiceResponse<Map<String, String>>> processPayment(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.status(201).body(new ServiceResponse<>(
                Map.of(
                        "paymentId", "pay-" + System.currentTimeMillis(),
                        "status", "ACCEPTED",
                        "amount", String.valueOf(body.getOrDefault("amount", "0"))),
                "payment-service", requestId, Instant.now().toString()));
    }

    @GetMapping("/{id}")
    ResponseEntity<ServiceResponse<Map<String, String>>> getPayment(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.ok(new ServiceResponse<>(
                Map.of("paymentId", id, "status", "SETTLED"),
                "payment-service", requestId, Instant.now().toString()));
    }

    record ServiceResponse<T>(T data, String service, String requestId, String timestamp) {}
}
