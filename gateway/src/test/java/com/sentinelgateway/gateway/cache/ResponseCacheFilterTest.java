package com.sentinelgateway.gateway.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Phase 11: Per-Route Response Caching.
 *
 * These are plain unit tests ({@link MockitoExtension}) that do not require a
 * Spring context or a live Redis instance.  The cache filter bean is
 * {@code @ConditionalOnProperty(sentinel.cache.enabled=true)}, so it is absent
 * in integration tests (where the property is {@code false}).
 *
 * The only production logic that can be tested in isolation is the static
 * {@link ResponseCacheFilter#buildCacheKey} helper, which encapsulates the
 * key-building contract that the rest of the caching code depends on.
 */
@ExtendWith(MockitoExtension.class)
class ResponseCacheFilterTest {

    // ── cache key format ──────────────────────────────────────────────────────

    @Test
    void cacheKey_withQuery_includesQueryAfterQuestionMark() {
        String key = ResponseCacheFilter.buildCacheKey("my-route", "/api/orders/1", "status=OPEN&page=2");
        assertThat(key).isEqualTo("sentinel:cache:my-route:/api/orders/1?status=OPEN&page=2");
    }

    @Test
    void cacheKey_withoutQuery_omitsQuestionMark() {
        String key = ResponseCacheFilter.buildCacheKey("my-route", "/api/orders/1", null);
        assertThat(key).isEqualTo("sentinel:cache:my-route:/api/orders/1");
    }

    @Test
    void cacheKey_emptyQuery_omitsQuestionMark() {
        String key = ResponseCacheFilter.buildCacheKey("my-route", "/api/orders/1", "");
        assertThat(key).isEqualTo("sentinel:cache:my-route:/api/orders/1");
    }

    @Test
    void cacheKey_prefixIsCorrect() {
        String key = ResponseCacheFilter.buildCacheKey("route-id", "/api/path", "q=1");
        assertThat(key).startsWith("sentinel:cache:");
    }

    @Test
    void cacheKey_containsRouteIdAndPath() {
        String routeId = "payment-service";
        String path    = "/api/payments/42";
        String key     = ResponseCacheFilter.buildCacheKey(routeId, path, null);

        assertThat(key).contains(routeId);
        assertThat(key).contains(path);
    }

    @Test
    void cacheKey_differentRoutesProduceDifferentKeys() {
        String key1 = ResponseCacheFilter.buildCacheKey("route-a", "/api/foo", null);
        String key2 = ResponseCacheFilter.buildCacheKey("route-b", "/api/foo", null);
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void cacheKey_differentPathsProduceDifferentKeys() {
        String key1 = ResponseCacheFilter.buildCacheKey("my-route", "/api/foo", null);
        String key2 = ResponseCacheFilter.buildCacheKey("my-route", "/api/bar", null);
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void cacheKey_differentQueriesProduceDifferentKeys() {
        String key1 = ResponseCacheFilter.buildCacheKey("my-route", "/api/items", "page=1");
        String key2 = ResponseCacheFilter.buildCacheKey("my-route", "/api/items", "page=2");
        assertThat(key1).isNotEqualTo(key2);
    }

    // ── CacheProperties default ───────────────────────────────────────────────

    @Test
    void cacheProperties_defaultEnabledIsFalse() {
        CacheProperties props = new CacheProperties();
        assertThat(props.isEnabled())
                .as("CacheProperties.enabled must default to false so the filter is absent in tests")
                .isFalse();
    }

    @Test
    void cacheProperties_canBeEnabled() {
        CacheProperties props = new CacheProperties();
        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }
}
