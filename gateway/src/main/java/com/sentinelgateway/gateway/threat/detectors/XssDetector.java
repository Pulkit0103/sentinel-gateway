package com.sentinelgateway.gateway.threat.detectors;

import com.sentinelgateway.gateway.threat.ThreatDetector;
import com.sentinelgateway.gateway.threat.ThreatPattern;
import com.sentinelgateway.gateway.threat.ThreatSignal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects cross-site scripting (XSS) patterns in the request URI.
 *
 * Checks for script tags, event handlers, javascript: URIs, and
 * common XSS vector tags.  The Referer and User-Agent headers are also
 * scanned because reflected XSS can arrive via any untrusted header.
 */
@Component
public class XssDetector implements ThreatDetector {

    private static final Pattern XSS_PATTERN = Pattern.compile(
            "<\\s*script[\\s>]"          // <script or <script>
            + "|<\\s*/\\s*script\\s*>"   // </script>
            + "|javascript\\s*:"         // javascript:
            + "|on\\w+\\s*="             // onerror= onload= onclick= etc.
            + "|<\\s*iframe[\\s>]"       // <iframe
            + "|<\\s*img[\\s>]"          // <img
            + "|expression\\s*\\("       // CSS expression()
            + "|vbscript\\s*:",          // vbscript:
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> SCANNED_HEADERS = List.of("Referer", "User-Agent");

    @Override
    public List<ThreatSignal> detect(ServerWebExchange exchange) {
        var req = exchange.getRequest();

        // Check URL (path + query, decoded so encoded attacks are caught)
        String target = DetectorUtils.decodedTarget(exchange);
        if (XSS_PATTERN.matcher(target).find()) {
            return List.of(new ThreatSignal(ThreatPattern.XSS, DetectorUtils.truncate(target)));
        }

        // Check selected headers
        for (String header : SCANNED_HEADERS) {
            String value = req.getHeaders().getFirst(header);
            if (value != null && XSS_PATTERN.matcher(value).find()) {
                return List.of(new ThreatSignal(ThreatPattern.XSS, "header:" + header));
            }
        }

        return List.of();
    }
}
