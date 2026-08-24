package com.sentinelgateway.gateway.adminaudit;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * In-memory ring buffer of recent admin API operations (Phase 31).
 *
 * Holds up to {@value #MAX_RECORDS} entries; oldest are evicted when full.
 * Thread-safe via {@link LinkedBlockingDeque}.
 */
@Component
public class AdminAuditRegistry {

    public static final int MAX_RECORDS = 500;

    private final LinkedBlockingDeque<AdminAuditRecord> records =
            new LinkedBlockingDeque<>(MAX_RECORDS);

    public void record(AdminAuditRecord entry) {
        if (!records.offerLast(entry)) {
            // Ring buffer full: drop oldest, retry
            records.pollFirst();
            records.offerLast(entry);
        }
    }

    /** Returns all records from oldest to newest. */
    public List<AdminAuditRecord> all() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }
}
