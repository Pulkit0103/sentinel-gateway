package com.sentinelgateway.gateway.adminaudit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminAuditRegistry} (Phase 31: Admin Operation Audit Trail).
 */
class AdminAuditRegistryTest {

    private AdminAuditRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AdminAuditRegistry();
    }

    private AdminAuditRecord rec(String path) {
        return new AdminAuditRecord(Instant.now(), "admin-user", "GET", path, 200);
    }

    @Test
    void freshRegistry_isEmpty() {
        assertThat(registry.size()).isZero();
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void record_addsEntry() {
        registry.record(rec("/admin/sessions"));
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void all_returnsRecordsInOrder() {
        registry.record(rec("/admin/a"));
        registry.record(rec("/admin/b"));
        assertThat(registry.all()).extracting(AdminAuditRecord::path)
                .containsExactly("/admin/a", "/admin/b");
    }

    @Test
    void clear_removesAllRecords() {
        registry.record(rec("/admin/a"));
        registry.record(rec("/admin/b"));
        registry.clear();
        assertThat(registry.size()).isZero();
    }

    @Test
    void ringBuffer_evictsOldestWhenFull() {
        for (int i = 0; i < AdminAuditRegistry.MAX_RECORDS; i++) {
            registry.record(rec("/admin/req-" + i));
        }
        assertThat(registry.size()).isEqualTo(AdminAuditRegistry.MAX_RECORDS);

        // Adding one more should evict the oldest and keep capacity
        registry.record(rec("/admin/overflow"));
        assertThat(registry.size()).isEqualTo(AdminAuditRegistry.MAX_RECORDS);
        // The last entry should be the overflow one
        List<AdminAuditRecord> all = registry.all();
        assertThat(all.get(all.size() - 1).path()).isEqualTo("/admin/overflow");
        // The first entry should be req-1 (req-0 was evicted)
        assertThat(all.get(0).path()).isEqualTo("/admin/req-1");
    }

    @Test
    void record_capturesAllFields() {
        Instant before = Instant.now();
        registry.record(new AdminAuditRecord(Instant.now(), "bob", "POST", "/admin/route-blocks", 200));
        AdminAuditRecord r = registry.all().get(0);
        assertThat(r.subject()).isEqualTo("bob");
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.path()).isEqualTo("/admin/route-blocks");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.timestamp()).isAfterOrEqualTo(before);
    }
}
