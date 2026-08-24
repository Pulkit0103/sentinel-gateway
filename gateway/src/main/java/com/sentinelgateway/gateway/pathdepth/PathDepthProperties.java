package com.sentinelgateway.gateway.pathdepth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sentinel.path-depth")
public class PathDepthProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
