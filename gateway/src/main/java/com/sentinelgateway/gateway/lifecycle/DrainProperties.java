package com.sentinelgateway.gateway.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for graceful-shutdown drain behaviour.
 *
 * Bound from the {@code sentinel.drain} prefix in application.yml.
 *
 * During a rolling deployment:
 * <ol>
 *   <li>The load balancer is told to stop routing new traffic (readiness probe fails)</li>
 *   <li>In-flight requests are allowed to complete for up to {@link #timeoutSeconds} seconds</li>
 *   <li>The JVM exits cleanly after draining</li>
 * </ol>
 *
 * The actual drain timeout is enforced by Spring Boot's built-in graceful-shutdown
 * mechanism ({@code server.shutdown=graceful} +
 * {@code spring.lifecycle.timeout-per-shutdown-phase}).  This class holds
 * the sentinel-specific copy of that value so it can be surfaced via the
 * {@code POST /admin/shutdown} response.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.drain")
public class DrainProperties {

    /** Master switch — set to {@code false} to skip drain and shut down immediately. */
    private boolean enabled = true;

    /** Maximum seconds to wait for in-flight requests to complete before forcing shutdown. */
    private int timeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
