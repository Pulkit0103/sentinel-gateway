package com.sentinelgateway.gateway.transform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "sentinel.transform")
public class TransformProperties {

    private List<String> sanitizeRequestHeaders = List.of(
            "X-User-Id", "X-Tenant-Id", "X-User-Roles", "X-Auth-Type");

    private List<String> removeResponseHeaders = List.of(
            "Server", "X-Powered-By", "Via");

    public List<String> getSanitizeRequestHeaders() {
        return sanitizeRequestHeaders;
    }

    public void setSanitizeRequestHeaders(List<String> sanitizeRequestHeaders) {
        this.sanitizeRequestHeaders = sanitizeRequestHeaders != null ? sanitizeRequestHeaders : List.of();
    }

    public List<String> getRemoveResponseHeaders() {
        return removeResponseHeaders;
    }

    public void setRemoveResponseHeaders(List<String> removeResponseHeaders) {
        this.removeResponseHeaders = removeResponseHeaders != null ? removeResponseHeaders : List.of();
    }
}
