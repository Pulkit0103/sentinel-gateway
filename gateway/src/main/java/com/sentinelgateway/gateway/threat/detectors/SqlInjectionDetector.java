package com.sentinelgateway.gateway.threat.detectors;

import com.sentinelgateway.gateway.threat.ThreatDetector;
import com.sentinelgateway.gateway.threat.ThreatPattern;
import com.sentinelgateway.gateway.threat.ThreatSignal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects common SQL injection patterns in the request URI (path and query).
 *
 * Checks for UNION-based, boolean-blind, error-based, and stacked-query
 * injection markers.  Body scanning is deferred to a future phase (requires
 * body buffering).
 */
@Component
public class SqlInjectionDetector implements ThreatDetector {

    private static final Pattern SQLI_PATTERN = Pattern.compile(
            "\\bUNION\\b.{0,20}\\bSELECT\\b"    // UNION SELECT
            + "|\\bOR\\b\\s+['\"]?\\s*\\d+\\s*=\\s*\\d"  // OR 1=1, OR '1'='1'
            + "|\\bDROP\\b\\s+\\bTABLE\\b"         // DROP TABLE
            + "|\\bINSERT\\b\\s+\\bINTO\\b"        // INSERT INTO
            + "|\\bDELETE\\b\\s+\\bFROM\\b"        // DELETE FROM
            + "|\\bEXEC\\s*\\("                    // EXEC(
            + "|\\bxp_cmdshell\\b"                 // xp_cmdshell
            + "|--\\s*$"                           // trailing SQL comment
            + "|;\\s*--",                          // statement terminator + comment
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public List<ThreatSignal> detect(ServerWebExchange exchange) {
        String target = DetectorUtils.decodedTarget(exchange);
        if (SQLI_PATTERN.matcher(target).find()) {
            return List.of(new ThreatSignal(ThreatPattern.SQL_INJECTION, DetectorUtils.truncate(target)));
        }
        return List.of();
    }
}
