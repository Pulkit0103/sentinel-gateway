package com.sentinelgateway.gateway.threat.detectors;

import com.sentinelgateway.gateway.threat.ThreatDetector;
import com.sentinelgateway.gateway.threat.ThreatPattern;
import com.sentinelgateway.gateway.threat.ThreatSignal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects OS command injection patterns in the request URI.
 *
 * Checks for shell metacharacters and common command-injection payloads
 * in URL paths and query parameters.
 */
@Component
public class CommandInjectionDetector implements ThreatDetector {

    private static final Pattern CMD_PATTERN = Pattern.compile(
            ";\\s*(ls|cat|pwd|id|whoami|rm|wget|curl|bash|sh|python|perl)\\b"  // ; cmd
            + "|\\|\\s*(ls|cat|pwd|id|whoami|curl|wget|bash|sh)\\b"            // | cmd
            + "|&&\\s*(rm|wget|curl|bash|sh|python)\\b"                        // && cmd
            + "|`[^`]+`"                                                        // `backtick`
            + "|\\$\\([^)]+\\)"                                                 // $(cmd)
            + "|%0[aAdD]"                                                       // newline injection
            + "|\\beval\\s*\\(",                                                // eval(
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<ThreatSignal> detect(ServerWebExchange exchange) {
        String target = DetectorUtils.decodedTarget(exchange);
        if (CMD_PATTERN.matcher(target).find()) {
            return List.of(new ThreatSignal(ThreatPattern.COMMAND_INJECTION, DetectorUtils.truncate(target)));
        }
        return List.of();
    }
}
