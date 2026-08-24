package com.sentinelgateway.gateway.pathlength;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Ring-buffer registry of requests rejected by the path length guard (Phase 43).
 */
@Component
public class PathLengthRegistry {

    private final LinkedBlockingDeque<PathLengthRecord> deque;

    public PathLengthRegistry(PathLengthProperties properties) {
        this.deque = new LinkedBlockingDeque<>(properties.getMaxRecords());
    }

    public void record(PathLengthRecord rec) {
        if (!deque.offerLast(rec)) {
            deque.pollFirst();
            deque.offerLast(rec);
        }
    }

    public List<PathLengthRecord> snapshot() {
        return List.copyOf(deque);
    }

    public int count() {
        return deque.size();
    }

    public void clear() {
        deque.clear();
    }
}
