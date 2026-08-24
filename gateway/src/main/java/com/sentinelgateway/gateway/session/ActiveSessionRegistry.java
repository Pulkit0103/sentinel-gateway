package com.sentinelgateway.gateway.session;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory registry of recently-active JWT subjects.
 *
 * Each call to {@link #recordActivity(String)} updates the last-seen timestamp for a subject.
 * {@link #activeSince(Instant)} returns subjects that have been seen at or after a given instant,
 * enabling sliding-window active session counts.
 *
 * Thread-safe via ConcurrentHashMap. State is ephemeral.
 */
@Component
public class ActiveSessionRegistry {

    /** Map of JWT subject → last activity timestamp. */
    private final ConcurrentHashMap<String, Instant> lastSeen = new ConcurrentHashMap<>();

    public void recordActivity(String subject) {
        if (subject != null && !subject.isBlank()) {
            lastSeen.put(subject, Instant.now());
        }
    }

    /**
     * Returns subjects last seen at or after {@code since}.
     * Use {@code Instant.now().minusSeconds(windowSeconds)} for a sliding window.
     */
    public Set<String> activeSince(Instant since) {
        return lastSeen.entrySet().stream()
                .filter(e -> !e.getValue().isBefore(since))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public int totalTracked() {
        return lastSeen.size();
    }

    public void evictBefore(Instant cutoff) {
        lastSeen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    public void clear() {
        lastSeen.clear();
    }
}
