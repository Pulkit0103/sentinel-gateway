package com.sentinelgateway.gateway.admin;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin endpoint exposing Resilience4j circuit breaker state.
 *
 * GET  /admin/circuit-breakers                — list all registered CBs with state + metrics
 * POST /admin/circuit-breakers/{name}/reset   — force CB transition to CLOSED
 *
 * Circuit breakers are named {routeId}-cb and are created lazily when a route is
 * first exercised. The registry may be empty on a fresh instance with no traffic.
 */
@RestController
@RequestMapping("/admin/circuit-breakers")
public class CircuitBreakerStateController {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerStateController.class);

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerStateController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Mono<Map<String, Object>> listCircuitBreakers() {
        List<Map<String, Object>> cbs = registry.getAllCircuitBreakers().stream()
                .map(this::toSnapshot)
                .sorted((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")))
                .collect(Collectors.toList());

        return Mono.just(Map.of(
                "circuitBreakers", cbs,
                "count", cbs.size()
        ));
    }

    @PostMapping("/{name}/reset")
    public Mono<ResponseEntity<Map<String, Object>>> resetCircuitBreaker(@PathVariable String name) {
        if (!registry.find(name).isPresent()) {
            return Mono.just(ResponseEntity.notFound().<Map<String, Object>>build());
        }

        CircuitBreaker cb = registry.circuitBreaker(name);
        String stateBefore = cb.getState().name();
        cb.reset();
        log.info("Circuit breaker '{}' reset from {} to CLOSED by admin request", name, stateBefore);

        return Mono.just(ResponseEntity.ok(Map.of(
                "name", name,
                "stateBefore", stateBefore,
                "stateAfter", cb.getState().name()
        )));
    }

    private Map<String, Object> toSnapshot(CircuitBreaker cb) {
        CircuitBreaker.Metrics m = cb.getMetrics();
        return Map.of(
                "name", cb.getName(),
                "state", cb.getState().name(),
                "failureRate", m.getFailureRate(),
                "slowCallRate", m.getSlowCallRate(),
                "numberOfBufferedCalls", m.getNumberOfBufferedCalls(),
                "numberOfFailedCalls", m.getNumberOfFailedCalls(),
                "numberOfSuccessfulCalls", m.getNumberOfSuccessfulCalls()
        );
    }
}
