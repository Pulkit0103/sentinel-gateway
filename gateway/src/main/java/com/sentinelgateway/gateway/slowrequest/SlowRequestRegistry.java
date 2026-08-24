package com.sentinelgateway.gateway.slowrequest;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Ring-buffer registry of recently detected slow requests (Phase 33).
 *
 * Capacity is set at construction time from {@link SlowRequestProperties#getMaxRecords()}.
 * Oldest entries are evicted when the buffer is full. Thread-safe.
 */
@Component
public class SlowRequestRegistry {

    private final int capacity;
    private final LinkedBlockingDeque<SlowRequestRecord> records;

    public SlowRequestRegistry(SlowRequestProperties properties) {
        this.capacity = properties.getMaxRecords();
        this.records = new LinkedBlockingDeque<>(this.capacity);
    }

    public void record(SlowRequestRecord entry) {
        if (!records.offerLast(entry)) {
            records.pollFirst();
            records.offerLast(entry);
        }
    }

    /** Returns all records from oldest to newest. */
    public List<SlowRequestRecord> all() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }
}
