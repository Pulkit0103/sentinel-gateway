package com.sentinelgateway.gateway.threat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the threat detection system.
 *
 * Risk thresholds determine what action is taken for a given total score:
 *   score < logThreshold                      → ALLOW (silent pass-through)
 *   logThreshold ≤ score < blockThreshold     → LOG (pass with INFO log)
 *   blockThreshold ≤ score < alertThreshold   → BLOCK (400 Bad Request)
 *   score ≥ alertThreshold                    → BLOCK_AND_ALERT (400 + ERROR log)
 */
@Component
@ConfigurationProperties(prefix = "sentinel.threat-detection")
public class ThreatDetectionProperties {

    private int logThreshold   = 25;
    private int blockThreshold = 60;
    private int alertThreshold = 90;
    private List<String> blockedIps = new ArrayList<>();

    public int getLogThreshold()                    { return logThreshold; }
    public void setLogThreshold(int v)              { this.logThreshold = v; }

    public int getBlockThreshold()                  { return blockThreshold; }
    public void setBlockThreshold(int v)            { this.blockThreshold = v; }

    public int getAlertThreshold()                  { return alertThreshold; }
    public void setAlertThreshold(int v)            { this.alertThreshold = v; }

    public List<String> getBlockedIps()             { return blockedIps; }
    public void setBlockedIps(List<String> v)       { this.blockedIps = v != null ? v : new ArrayList<>(); }
}
