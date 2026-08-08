package com.sentinelgateway.gateway.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Global resilience defaults applied to every registered route.
 *
 * Response timeout is NOT configurable here — set it via
 * spring.cloud.gateway.httpclient.response-timeout (global) or per-environment override.
 * Spring Cloud Gateway 4.x does not expose a per-route timeout through the programmatic DSL.
 *
 * Individual circuit-breaker behaviour can be tuned via Resilience4j instance configuration:
 *   resilience4j.circuitbreaker.instances.{routeId}-cb.*
 */
@Component
@ConfigurationProperties(prefix = "sentinel.resilience")
public class ResilienceProperties {

    /** Master switch — set to false to disable all resilience filters globally. */
    private boolean enabled = true;

    private Retry retry = new Retry();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public static class Retry {
        private boolean enabled = true;
        /** Number of retry attempts (not counting the initial request). */
        private int attempts = 2;
        /** HTTP methods eligible for retry — only idempotent methods by default. */
        private List<String> safeMethods = List.of("GET", "HEAD");

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }

        public List<String> getSafeMethods() { return safeMethods; }
        public void setSafeMethods(List<String> safeMethods) { this.safeMethods = safeMethods; }
    }

    public static class CircuitBreaker {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
