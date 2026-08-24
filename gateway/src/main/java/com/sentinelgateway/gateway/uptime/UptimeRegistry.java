package com.sentinelgateway.gateway.uptime;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory registry tracking gateway start time and request count (Phase 30).
 *
 * Thread-safe; state is ephemeral (resets on restart).
 */
@Component
public class UptimeRegistry {

    private final Instant startTime = Instant.now();
    private final AtomicLong requestCount = new AtomicLong(0);

    public void recordRequest() {
        requestCount.incrementAndGet();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    public long uptimeSeconds() {
        return java.time.Duration.between(startTime, Instant.now()).getSeconds();
    }

    public void reset() {
        requestCount.set(0);
    }
}
