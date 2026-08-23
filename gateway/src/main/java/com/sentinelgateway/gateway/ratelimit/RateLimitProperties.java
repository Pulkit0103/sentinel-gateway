package com.sentinelgateway.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "sentinel.rate-limit")
public class RateLimitProperties {

    private Map<String, PolicyConfig> policies = new HashMap<>();

    public Map<String, PolicyConfig> getPolicies() { return policies; }
    public void setPolicies(Map<String, PolicyConfig> policies) { this.policies = policies; }

    public long limitFor(RateLimitPolicy policy) {
        PolicyConfig cfg = policies.get(policy.name());
        if (cfg != null) return cfg.getRequestsPerMinute();
        cfg = policies.get("DEFAULT");
        return cfg != null ? cfg.getRequestsPerMinute() : 1000L;
    }

    public static class PolicyConfig {
        private long requestsPerMinute = 1000;
        public long getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(long requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    }
}
