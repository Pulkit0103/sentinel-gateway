package com.sentinelgateway.gateway.threat;

/**
 * Enumeration of detectable attack pattern categories.
 *
 * Each pattern carries a base risk score that contributes to the total
 * request risk score.  Scores are additive — a request with both SQLi and
 * XSS patterns accumulates both scores, raising the risk level.
 */
public enum ThreatPattern {

    PATH_TRAVERSAL(30,  "Directory traversal attempt (../ variants)"),
    SQL_INJECTION(60,   "SQL injection pattern detected"),
    XSS(50,             "Cross-site scripting pattern detected"),
    COMMAND_INJECTION(80, "OS command injection pattern detected");

    private final int baseScore;
    private final String description;

    ThreatPattern(int baseScore, String description) {
        this.baseScore = baseScore;
        this.description = description;
    }

    public int baseScore()      { return baseScore; }
    public String description() { return description; }
}
