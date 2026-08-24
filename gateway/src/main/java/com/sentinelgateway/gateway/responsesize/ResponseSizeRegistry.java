package com.sentinelgateway.gateway.responsesize;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ring-buffer registry of recent response body sizes (Phase 42).
 *
 * Also maintains running totals for bandwidth estimation. Only responses that set
 * a Content-Length header are tracked; chunked/streaming responses are skipped.
 */
@Component
public class ResponseSizeRegistry {

    private final LinkedBlockingDeque<Long> samples;
    private final AtomicLong totalBytes = new AtomicLong();
    private final AtomicLong sampleCount = new AtomicLong();

    public ResponseSizeRegistry(ResponseSizeProperties properties) {
        this.samples = new LinkedBlockingDeque<>(properties.getMaxRecords());
    }

    public void record(long contentLengthBytes) {
        if (!samples.offerLast(contentLengthBytes)) {
            samples.pollFirst();
            samples.offerLast(contentLengthBytes);
        }
        totalBytes.addAndGet(contentLengthBytes);
        sampleCount.incrementAndGet();
    }

    /** Snapshot of recent sample sizes (up to maxRecords). */
    public List<Long> recentSamples() {
        return new ArrayList<>(samples);
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public long getSampleCount() {
        return sampleCount.get();
    }

    public long getAverageBytes() {
        long count = sampleCount.get();
        return count == 0 ? 0 : totalBytes.get() / count;
    }

    public void reset() {
        samples.clear();
        totalBytes.set(0);
        sampleCount.set(0);
    }
}
