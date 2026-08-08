package com.sentinelgateway.gateway.threat.detectors;

import com.sentinelgateway.gateway.threat.ThreatDetector;
import com.sentinelgateway.gateway.threat.ThreatPattern;
import com.sentinelgateway.gateway.threat.ThreatSignal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects directory traversal attempts in the request URI (path and query).
 *
 * Patterns checked:
 *   - ../ or ..\ (encoded and unencoded variants)
 *   - %2e%2e (URL-encoded dot-dot)
 *   - ..%2f or ..%5c (partial encoding)
 */
@Component
public class PathTraversalDetector implements ThreatDetector {

    private static final Pattern TRAVERSAL_PATTERN = Pattern.compile(
            "\\.\\./|\\.\\.\\\\" +          // ../ or ..\
            "|%2e%2e[%/\\\\]" +              // %2e%2e/ or %2e%2e\ or %2e%2e%
            "|\\.\\.%2[fF]" +                // ..%2f or ..%2F
            "|\\.\\.%5[cC]",                 // ..%5c or ..%5C
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<ThreatSignal> detect(ServerWebExchange exchange) {
        String target = DetectorUtils.decodedTarget(exchange);
        if (TRAVERSAL_PATTERN.matcher(target).find()) {
            return List.of(new ThreatSignal(ThreatPattern.PATH_TRAVERSAL, DetectorUtils.truncate(target)));
        }
        return List.of();
    }
}
