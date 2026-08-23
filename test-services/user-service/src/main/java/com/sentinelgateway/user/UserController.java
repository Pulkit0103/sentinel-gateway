package com.sentinelgateway.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
class UserController {

    @GetMapping
    ResponseEntity<ServiceResponse<List<Map<String, String>>>> listUsers(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.ok(new ServiceResponse<>(
                List.of(
                        Map.of("id", "user-1", "name", "Alice"),
                        Map.of("id", "user-2", "name", "Bob")),
                "user-service", requestId, Instant.now().toString()));
    }

    @GetMapping("/{id}")
    ResponseEntity<ServiceResponse<Map<String, String>>> getUser(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.ok(new ServiceResponse<>(
                Map.of("id", id, "name", "Alice"),
                "user-service", requestId, Instant.now().toString()));
    }

    @PostMapping
    ResponseEntity<ServiceResponse<Map<String, String>>> createUser(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return ResponseEntity.status(201).body(new ServiceResponse<>(
                Map.of("id", "user-new", "name", body.getOrDefault("name", "unknown")),
                "user-service", requestId, Instant.now().toString()));
    }

    record ServiceResponse<T>(T data, String service, String requestId, String timestamp) {}
}
