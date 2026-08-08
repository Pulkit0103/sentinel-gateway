package com.sentinelgateway.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sentinel Gateway application properties, bound from the {@code sentinel.gateway.*} namespace.
 * Sensitive values (passwords, secrets) are never placed here — use environment variables
 * or a secrets manager for those.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.gateway")
public class SentinelGatewayProperties {

    private String helloServiceUrl = "http://localhost:8081";

    public String getHelloServiceUrl() {
        return helloServiceUrl;
    }

    public void setHelloServiceUrl(String helloServiceUrl) {
        this.helloServiceUrl = helloServiceUrl;
    }
}
