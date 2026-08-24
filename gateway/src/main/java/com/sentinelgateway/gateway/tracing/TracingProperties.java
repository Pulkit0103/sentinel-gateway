package com.sentinelgateway.gateway.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for W3C Trace Context header propagation.
 *
 * sentinel.tracing.enabled          — master switch (default true)
 * sentinel.tracing.propagate-existing — if true, forward the client's traceparent
 *                                       when it is valid W3C format (default true)
 */
@Component
@ConfigurationProperties(prefix = "sentinel.tracing")
public class TracingProperties {

    private boolean enabled = true;
    private boolean propagateExisting = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPropagateExisting() {
        return propagateExisting;
    }

    public void setPropagateExisting(boolean propagateExisting) {
        this.propagateExisting = propagateExisting;
    }
}
