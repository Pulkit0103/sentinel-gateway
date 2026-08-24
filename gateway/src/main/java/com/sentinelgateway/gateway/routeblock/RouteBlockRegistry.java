package com.sentinelgateway.gateway.routeblock;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of currently blocked route IDs.
 *
 * Thread-safe; backed by a {@link ConcurrentHashMap} set.
 * Changes take effect on the next request — no restart required.
 * State is lost on restart (intentional: blocks are an emergency operator action).
 */
@Component
public class RouteBlockRegistry {

    private final Set<String> blockedRouteIds = ConcurrentHashMap.newKeySet();

    public void block(String routeId) {
        blockedRouteIds.add(routeId);
    }

    public void unblock(String routeId) {
        blockedRouteIds.remove(routeId);
    }

    public boolean isBlocked(String routeId) {
        return routeId != null && blockedRouteIds.contains(routeId);
    }

    /** Snapshot of currently blocked route IDs. */
    public Set<String> blockedRouteIds() {
        return Collections.unmodifiableSet(blockedRouteIds);
    }

    public void unblockAll() {
        blockedRouteIds.clear();
    }
}
