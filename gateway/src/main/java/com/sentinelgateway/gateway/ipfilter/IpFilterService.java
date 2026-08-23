package com.sentinelgateway.gateway.ipfilter;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.List;

/**
 * Utility service for CIDR-based IP filtering.
 *
 * Logic:
 * 1. If {@code allowedCidrs} is non-empty, the client IP must match at least one — otherwise deny (403).
 * 2. If {@code blockedCidrs} is non-empty and the client IP matches any entry — deny (403).
 * 3. If both lists are empty, allow (no restriction).
 *
 * Allowlist check runs first, then denylist check.
 */
@Service
public class IpFilterService {

    /**
     * Returns {@code true} if the client IP is allowed through the configured lists.
     *
     * @param clientIp     the client's IP address (dotted-decimal or IPv6)
     * @param allowedCidrs list of allowed CIDR ranges; empty means "no allowlist restriction"
     * @param blockedCidrs list of blocked CIDR ranges; empty means "no denylist restriction"
     * @return {@code true} if the request should proceed, {@code false} if it should be blocked
     */
    public boolean isAllowed(String clientIp, List<String> allowedCidrs, List<String> blockedCidrs) {
        // Allowlist: if non-empty, client must match at least one entry
        if (allowedCidrs != null && !allowedCidrs.isEmpty()) {
            boolean matchesAllowlist = allowedCidrs.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(cidr -> matchesCidr(clientIp, cidr));
            if (!matchesAllowlist) {
                return false;
            }
        }

        // Denylist: if client matches any blocked entry, deny
        if (blockedCidrs != null && !blockedCidrs.isEmpty()) {
            boolean matchesDenylist = blockedCidrs.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(cidr -> matchesCidr(clientIp, cidr));
            if (matchesDenylist) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether a given IP address falls within the specified CIDR range.
     * Supports both exact IP comparison (no prefix) and CIDR notation (e.g., 10.0.0.0/8).
     */
    private boolean matchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) return cidr.equals(ip);
            String[] parts = cidr.split("/");
            int prefix = Integer.parseInt(parts[1]);
            byte[] addr = InetAddress.getByName(ip).getAddress();
            byte[] net = InetAddress.getByName(parts[0]).getAddress();
            if (addr.length != net.length) return false;
            int full = prefix / 8, remain = prefix % 8;
            for (int i = 0; i < full; i++) if (addr[i] != net[i]) return false;
            if (remain > 0) {
                int mask = 0xFF & (0xFF << (8 - remain));
                return (addr[full] & mask) == (net[full] & mask);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
