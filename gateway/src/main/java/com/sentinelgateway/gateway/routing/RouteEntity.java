package com.sentinelgateway.gateway.routing;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent route definition stored in the {@code routes} table.
 *
 * Methods and requiredScopes are stored as comma-separated strings;
 * addRequestHeaders is stored as comma-separated key=value pairs.
 */
@Table("routes")
public class RouteEntity {

    @Id
    private Long id;

    @Column("route_id")
    private String routeId;

    @Column("path")
    private String path;

    @Column("service_uri")
    private String serviceUri;

    @Column("methods")
    private String methods;

    @Column("enabled")
    private boolean enabled = true;

    @Column("required_scopes")
    private String requiredScopes;

    @Column("tenant_required")
    private boolean tenantRequired = false;

    @Column("rate_limit_policy")
    private String rateLimitPolicy = "DEFAULT";

    @Column("strip_prefix")
    private int stripPrefix = 0;

    @Column("add_request_headers")
    private String addRequestHeaders;

    @Column("allowed_ips")
    private String allowedIps;

    @Column("blocked_ips")
    private String blockedIps;

    @Column("max_body_bytes")
    private Long maxBodyBytes;

    @Column("timeout_ms")
    private Long timeoutMs;

    @Column("cache_ttl_seconds")
    private Integer cacheTtlSeconds;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public RouteEntity() {}

    public List<String> methodList() {
        return splitOrEmpty(methods);
    }

    public List<String> requiredScopeList() {
        return splitOrEmpty(requiredScopes);
    }

    public Map<String, String> addRequestHeaderMap() {
        if (addRequestHeaders == null || addRequestHeaders.isBlank()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : addRequestHeaders.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return result;
    }

    public List<String> allowedIpList() {
        return splitOrEmpty(allowedIps);
    }

    public List<String> blockedIpList() {
        return splitOrEmpty(blockedIps);
    }

    public RouteDefinition toDomain() {
        return new RouteDefinition(
                routeId, path, serviceUri,
                methodList(), enabled,
                requiredScopeList(), tenantRequired,
                rateLimitPolicy != null ? rateLimitPolicy : "DEFAULT",
                stripPrefix,
                addRequestHeaderMap(),
                allowedIpList(),
                blockedIpList(),
                maxBodyBytes,
                timeoutMs,
                cacheTtlSeconds
        );
    }

    public static RouteEntity from(RouteDefinition d) {
        RouteEntity e = new RouteEntity();
        e.routeId = d.routeId();
        e.path = d.path();
        e.serviceUri = d.serviceUri();
        e.methods = joinOrNull(d.methods());
        e.enabled = d.enabled();
        e.requiredScopes = joinOrNull(d.requiredScopes());
        e.tenantRequired = d.tenantRequired();
        e.rateLimitPolicy = d.rateLimitPolicy();
        e.stripPrefix = d.stripPrefix();
        e.addRequestHeaders = encodeHeaders(d.addRequestHeaders());
        e.allowedIps = joinOrNull(d.allowedIps());
        e.blockedIps = joinOrNull(d.blockedIps());
        e.maxBodyBytes = d.maxBodyBytes();
        e.timeoutMs = d.timeoutMs();
        e.cacheTtlSeconds = d.cacheTtlSeconds();
        e.createdAt = LocalDateTime.now();
        e.updatedAt = LocalDateTime.now();
        return e;
    }

    private static String joinOrNull(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    private static List<String> splitOrEmpty(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String encodeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        headers.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(',');
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }
    public String getRouteId()                 { return routeId; }
    public void setRouteId(String v)           { this.routeId = v; }
    public String getPath()                    { return path; }
    public void setPath(String v)              { this.path = v; }
    public String getServiceUri()              { return serviceUri; }
    public void setServiceUri(String v)        { this.serviceUri = v; }
    public String getMethods()                 { return methods; }
    public void setMethods(String v)           { this.methods = v; }
    public boolean isEnabled()                 { return enabled; }
    public void setEnabled(boolean v)          { this.enabled = v; }
    public String getRequiredScopes()          { return requiredScopes; }
    public void setRequiredScopes(String v)    { this.requiredScopes = v; }
    public boolean isTenantRequired()          { return tenantRequired; }
    public void setTenantRequired(boolean v)   { this.tenantRequired = v; }
    public String getRateLimitPolicy()         { return rateLimitPolicy; }
    public void setRateLimitPolicy(String v)   { this.rateLimitPolicy = v; }
    public int getStripPrefix()                { return stripPrefix; }
    public void setStripPrefix(int v)          { this.stripPrefix = v; }
    public String getAddRequestHeaders()       { return addRequestHeaders; }
    public void setAddRequestHeaders(String v) { this.addRequestHeaders = v; }
    public String getAllowedIps()              { return allowedIps; }
    public void setAllowedIps(String v)        { this.allowedIps = v; }
    public String getBlockedIps()              { return blockedIps; }
    public void setBlockedIps(String v)        { this.blockedIps = v; }
    public Long getMaxBodyBytes()              { return maxBodyBytes; }
    public void setMaxBodyBytes(Long v)        { this.maxBodyBytes = v; }
    public Long getTimeoutMs()                 { return timeoutMs; }
    public void setTimeoutMs(Long v)           { this.timeoutMs = v; }
    public Integer getCacheTtlSeconds()        { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(Integer v)  { this.cacheTtlSeconds = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }
}
