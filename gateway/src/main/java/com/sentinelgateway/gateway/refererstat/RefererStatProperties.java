package com.sentinelgateway.gateway.refererstat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sentinel.referer-stats")
public class RefererStatProperties {

    private boolean enabled = true;
    private int topN = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTopN() { return topN; }
    public void setTopN(int topN) { this.topN = topN; }
}
