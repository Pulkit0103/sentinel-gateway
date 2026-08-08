package com.sentinelgateway.gateway.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        // Disable Spring Cloud Gateway autoconfiguration — this test only exercises RouteRegistry,
        // not the Netty/reactor web stack that GatewayAutoConfiguration requires.
        "spring.cloud.gateway.enabled=false",

        "sentinel.gateway.routes[0].route-id=user-service",
        "sentinel.gateway.routes[0].path=/api/users/**",
        "sentinel.gateway.routes[0].service-uri=http://localhost:8082",
        "sentinel.gateway.routes[0].methods=GET,POST",
        "sentinel.gateway.routes[0].enabled=true",

        "sentinel.gateway.routes[1].route-id=disabled-service",
        "sentinel.gateway.routes[1].path=/api/disabled/**",
        "sentinel.gateway.routes[1].service-uri=http://localhost:9999",
        "sentinel.gateway.routes[1].methods=GET",
        "sentinel.gateway.routes[1].enabled=false"
})
class RouteRegistryTest {

    @Autowired
    private RouteRegistry registry;

    @Test
    void allRoutes_returnsAllIncludingDisabled() {
        List<RouteDefinition> all = registry.allRoutes();
        assertThat(all).hasSize(2);
    }

    @Test
    void enabledRoutes_excludesDisabledRoutes() {
        List<RouteDefinition> enabled = registry.enabledRoutes();
        assertThat(enabled).hasSize(1);
        assertThat(enabled.get(0).routeId()).isEqualTo("user-service");
    }

    @Test
    void findById_existingRoute_returnsIt() {
        assertThat(registry.findById("user-service")).isPresent();
        assertThat(registry.findById("user-service").get().path()).isEqualTo("/api/users/**");
    }

    @Test
    void findById_unknownRoute_returnsEmpty() {
        assertThat(registry.findById("does-not-exist")).isEmpty();
    }

    @Test
    void findById_disabledRoute_stillReturnsIt() {
        // The registry holds all routes; the GatewayRoutingConfig decides not to register disabled ones.
        assertThat(registry.findById("disabled-service")).isPresent();
        assertThat(registry.findById("disabled-service").get().enabled()).isFalse();
    }
}
