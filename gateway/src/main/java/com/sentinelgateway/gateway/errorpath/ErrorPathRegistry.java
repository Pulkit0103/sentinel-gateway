package com.sentinelgateway.gateway.errorpath;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ring-buffer of recent error responses (4xx + 5xx) with per-bucket counters (Phase 56).
 */
@Component
public class ErrorPathRegistry {

    private volatile int maxRecords = 200;
    private final LinkedBlockingDeque<ErrorPathRecord> recent = new LinkedBlockingDeque<>(10000);
    private final AtomicLong total4xx = new AtomicLong();
    private final AtomicLong total5xx = new AtomicLong();

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public void record(ErrorPathRecord rec) {
        int status = rec.statusCode();
        if (status >= 400 && status < 500) total4xx.incrementAndGet();
        else if (status >= 500) total5xx.incrementAndGet();

        if (recent.size() >= maxRecords) {
            recent.pollFirst();
        }
        recent.offerLast(rec);
    }

    public Map<String, Object> snapshot() {
        List<ErrorPathRecord> list = new ArrayList<>(recent);
        Collections.reverse(list);
        return Map.of(
                "total4xx", total4xx.get(),
                "total5xx", total5xx.get(),
                "recentErrors", list
        );
    }

    public void reset() {
        recent.clear();
        total4xx.set(0);
        total5xx.set(0);
    }

    public long getTotal4xx() { return total4xx.get(); }
    public long getTotal5xx() { return total5xx.get(); }
    public int getRecordedCount() { return recent.size(); }
}
