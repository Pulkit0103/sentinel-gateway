package com.sentinelgateway.gateway.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runtime configuration for maintenance mode.
 *
 * Toggling {@code enabled} at runtime (via {@link MaintenanceController}) takes
 * effect immediately for all subsequent requests; the change is not persisted
 * across restarts.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.maintenance")
public class MaintenanceProperties {

    /** When true, all non-admin, non-actuator requests receive 503. */
    private boolean enabled = false;

    /** Body message returned in the 503 JSON response. */
    private String message = "Service temporarily unavailable for maintenance";

    /** Number of seconds clients should wait before retrying (Retry-After header). */
    private int retryAfterSeconds = 60;

    /**
     * Roles that may bypass maintenance mode.
     * Compared against Spring Security granted authority names (e.g. "ROLE_ADMIN").
     */
    private List<String> bypassRoles = List.of("ROLE_ADMIN");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
    public void setRetryAfterSeconds(int retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }

    public List<String> getBypassRoles() { return bypassRoles; }
    public void setBypassRoles(List<String> bypassRoles) { this.bypassRoles = bypassRoles; }
}
