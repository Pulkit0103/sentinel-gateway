package com.sentinelgateway.hello;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
class HelloController {

    @GetMapping("/api/hello")
    ResponseEntity<HelloResponse> hello(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {

        return ResponseEntity.ok(new HelloResponse(
                "Hello from Sentinel Gateway!",
                "hello-service",
                "healthy",
                requestId,
                Instant.now().toString()
        ));
    }

    record HelloResponse(
            String message,
            String service,
            String status,
            String requestId,
            String timestamp
    ) {}
}
