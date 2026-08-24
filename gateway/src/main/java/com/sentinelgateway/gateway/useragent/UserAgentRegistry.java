package com.sentinelgateway.gateway.useragent;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-category User-Agent request count registry (Phase 48).
 *
 * Normalises User-Agent strings into one of five categories:
 *   bot, mobile, browser, service, unknown
 * to prevent high-cardinality unbounded maps while still providing
 * actionable traffic-origin breakdowns.
 */
@Component
public class UserAgentRegistry {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();

    public void record(String userAgent) {
        String category = categorise(userAgent);
        counts.computeIfAbsent(category, k -> new AtomicLong()).incrementAndGet();
        total.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> perCategory = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perCategory.put(e.getKey(), e.getValue().get()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total.get());
        result.put("categories", Collections.unmodifiableMap(perCategory));
        return Collections.unmodifiableMap(result);
    }

    public void reset() {
        counts.clear();
        total.set(0);
    }

    static String categorise(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        String ua = userAgent.toLowerCase();

        // Bot detection: check before browser because Googlebot contains "Safari"
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("slurp") || ua.contains("scan") || ua.contains("scraper")) {
            return "bot";
        }

        // Mobile: Android, iPhone, iPad precede generic browser check
        if (ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")
                || ua.contains("mobile") || ua.contains("blackberry") || ua.contains("windows phone")) {
            return "mobile";
        }

        // Desktop browser
        if (ua.contains("mozilla") || ua.contains("chrome") || ua.contains("firefox")
                || ua.contains("safari") || ua.contains("edge") || ua.contains("opera")) {
            return "browser";
        }

        // API / programmatic client
        if (ua.contains("curl") || ua.contains("wget") || ua.contains("python")
                || ua.contains("java") || ua.contains("okhttp") || ua.contains("axios")
                || ua.contains("postman") || ua.contains("insomnia") || ua.contains("go-http")
                || ua.contains("apache-httpclient") || ua.contains("reactor")) {
            return "service";
        }

        return "service";
    }
}
