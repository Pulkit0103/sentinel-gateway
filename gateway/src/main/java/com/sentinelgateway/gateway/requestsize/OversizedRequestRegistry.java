package com.sentinelgateway.gateway.requestsize;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Ring-buffer registry for requests rejected by the body-size guard (Phase 37).
 *
 * Bounded by {@link RequestSizeProperties#getMaxRecords()}; oldest entries are evicted
 * when full using offerLast / pollFirst.
 */
@Component
public class OversizedRequestRegistry {

    private final LinkedBlockingDeque<OversizedRequestRecord> deque;

    public OversizedRequestRegistry(RequestSizeProperties properties) {
        this.deque = new LinkedBlockingDeque<>(properties.getMaxRecords());
    }

    public void record(OversizedRequestRecord rec) {
        if (!deque.offerLast(rec)) {
            deque.pollFirst();
            deque.offerLast(rec);
        }
    }

    public List<OversizedRequestRecord> snapshot() {
        return List.copyOf(deque);
    }

    public int count() {
        return deque.size();
    }

    public void clear() {
        deque.clear();
    }
}
