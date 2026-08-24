package com.sentinelgateway.gateway.ipaccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IpAccessProperties} (Phase 28: IP-Based Access Control).
 */
class IpAccessPropertiesTest {

    private IpAccessProperties props;

    @BeforeEach
    void setUp() {
        props = new IpAccessProperties();
    }

    @Test
    void defaults_disabled_denylist_emptyLists() {
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getMode()).isEqualTo(IpAccessProperties.Mode.DENYLIST);
        assertThat(props.getGlobalList()).isEmpty();
        assertThat(props.getRoutes()).isEmpty();
    }

    @Test
    void effectiveListForRoute_noRouteConfig_returnsGlobalList() {
        props.setGlobalList(List.of("1.2.3.4", "5.6.7.8"));
        assertThat(props.effectiveListForRoute("some-route")).containsExactly("1.2.3.4", "5.6.7.8");
    }

    @Test
    void effectiveListForRoute_nullRouteId_returnsGlobalList() {
        props.setGlobalList(List.of("10.0.0.1"));
        assertThat(props.effectiveListForRoute(null)).containsExactly("10.0.0.1");
    }

    @Test
    void effectiveListForRoute_routeHasOwnList_returnsRouteList() {
        props.setGlobalList(List.of("1.1.1.1"));
        props.setRoutes(Map.of("payment-service", List.of("192.168.1.1", "192.168.1.2")));
        assertThat(props.effectiveListForRoute("payment-service"))
                .containsExactlyInAnyOrder("192.168.1.1", "192.168.1.2");
    }

    @Test
    void effectiveListForRoute_unknownRouteId_returnsGlobalList() {
        props.setGlobalList(List.of("10.0.0.1"));
        props.setRoutes(Map.of("other-route", List.of("9.9.9.9")));
        assertThat(props.effectiveListForRoute("unrelated-route")).containsExactly("10.0.0.1");
    }

    @Test
    void modeCanBeSetToAllowlist() {
        props.setMode(IpAccessProperties.Mode.ALLOWLIST);
        assertThat(props.getMode()).isEqualTo(IpAccessProperties.Mode.ALLOWLIST);
    }

    @Test
    void enabledCanBeToggled() {
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }
}
