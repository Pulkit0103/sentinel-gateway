package com.sentinelgateway.gateway.routing;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Persistent route definition stored in the {@code routes} table.
 *
 * Methods and requiredScopes are stored as comma-separated strings;
 * use the list-returning helpers for business logic.
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

    public RouteDefinition toDomain() {
        return new RouteDefinition(
                routeId, path, serviceUri,
                methodList(), enabled,
                requiredScopeList(), tenantRequired,
                rateLimitPolicy != null ? rateLimitPolicy : "DEFAULT"
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
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }
}
