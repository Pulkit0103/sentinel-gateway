package com.sentinelgateway.gateway.queryparam;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sentinel.query-param-count")
public class QueryParamProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
