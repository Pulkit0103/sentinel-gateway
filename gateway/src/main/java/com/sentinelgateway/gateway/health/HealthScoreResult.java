package com.sentinelgateway.gateway.health;

import java.util.Map;

/**
 * Composite health score result (Phase 39).
 */
public record HealthScoreResult(int score, String grade, Map<String, Integer> factors) {}
