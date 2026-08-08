package com.sentinelgateway.gateway.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Seeds the security_policies table from YAML configuration at startup.
 *
 * Wipes any existing rows first so the YAML remains authoritative on each
 * restart (suitable for H2 dev/test; production deployments with a persistent
 * DB should swap this for an idempotent upsert via the Admin API).
 */
@Component
public class SecurityPolicyLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyLoader.class);

    private final SecurityPolicyRepository repository;
    private final SecurityPolicyProperties properties;

    public SecurityPolicyLoader(SecurityPolicyRepository repository,
                                SecurityPolicyProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SecurityPolicyProperties.PolicyEntry> entries = properties.getPolicies();
        if (entries.isEmpty()) {
            log.info("No security policies defined in configuration — skipping policy load");
            return;
        }

        List<SecurityPolicy> policies = entries.stream()
                .map(SecurityPolicyProperties.PolicyEntry::toSecurityPolicy)
                .toList();

        // Clear existing rows (YAML is authoritative at startup) then bulk-insert.
        // Called from the main thread (not a reactor thread), so blockLast() is safe.
        repository.deleteAll()
                .thenMany(repository.saveAll(policies))
                .blockLast(Duration.ofSeconds(15));

        log.info("Loaded {} security policies from configuration", policies.size());
        policies.forEach(p -> log.debug("  policy: {}", p));
    }
}
