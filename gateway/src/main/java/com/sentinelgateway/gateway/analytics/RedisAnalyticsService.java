package com.sentinelgateway.gateway.analytics;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Redis-backed implementation of {@link AnalyticsService}.
 *
 * Key schema:
 *   analytics:route:req:{date}       → ZSet  member=routeId,  score=count
 *   analytics:tenant:req:{date}      → ZSet  member=tenantId, score=count
 *   analytics:outcomes:{date}        → Hash  field=outcome,   value=count
 *   analytics:hourly:{yyyy-MM-dd-HH} → Hash  fields: total, errors, blocked, rate_limited
 */
public class RedisAnalyticsService implements AnalyticsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final AnalyticsProperties props;

    public RedisAnalyticsService(ReactiveStringRedisTemplate redisTemplate, AnalyticsProperties props) {
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    // ── record ────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> record(AnalyticsRecord rec) {
        String date    = LocalDate.now().format(DATE_FMT);
        String hourKey = LocalDateTime.now().format(HOUR_FMT);
        Duration routeTtl  = Duration.ofDays(props.getRetentionDays());
        Duration hourlyTtl = Duration.ofDays(2);

        // Route ZSet
        String routeKey = "analytics:route:req:" + date;
        Mono<Void> routeOp = Mono.empty();
        if (rec.routeId() != null) {
            routeOp = redisTemplate.opsForZSet()
                    .incrementScore(routeKey, rec.routeId(), 1.0)
                    .then(redisTemplate.expire(routeKey, routeTtl))
                    .then()
                    .onErrorResume(e -> Mono.empty());
        }

        // Tenant ZSet
        String tenantKey = "analytics:tenant:req:" + date;
        Mono<Void> tenantOp = Mono.empty();
        if (rec.tenantId() != null) {
            tenantOp = redisTemplate.opsForZSet()
                    .incrementScore(tenantKey, rec.tenantId(), 1.0)
                    .then(redisTemplate.expire(tenantKey, routeTtl))
                    .then()
                    .onErrorResume(e -> Mono.empty());
        }

        // Outcomes Hash
        String outcomeKey = "analytics:outcomes:" + date;
        Mono<Void> outcomeOp = redisTemplate.opsForHash()
                .increment(outcomeKey, rec.outcome(), 1)
                .then(redisTemplate.expire(outcomeKey, routeTtl))
                .then()
                .onErrorResume(e -> Mono.empty());

        // Hourly Hash
        String hourlyKey = "analytics:hourly:" + hourKey;
        Mono<Void> totalOp = redisTemplate.opsForHash()
                .increment(hourlyKey, "total", 1)
                .then()
                .onErrorResume(e -> Mono.empty());

        Mono<Void> hourlyExtra = buildHourlyExtraOp(hourlyKey, rec.outcome());

        Mono<Void> hourlyExpireOp = redisTemplate.expire(hourlyKey, hourlyTtl)
                .then()
                .onErrorResume(e -> Mono.empty());

        return Mono.when(routeOp, tenantOp, outcomeOp, totalOp, hourlyExtra, hourlyExpireOp);
    }

    private Mono<Void> buildHourlyExtraOp(String hourlyKey, String outcome) {
        if (outcome == null) return Mono.empty();
        return switch (outcome) {
            case "ERROR" -> redisTemplate.opsForHash()
                    .increment(hourlyKey, "errors", 1).then()
                    .onErrorResume(e -> Mono.empty());
            case "BLOCKED_WAF" -> redisTemplate.opsForHash()
                    .increment(hourlyKey, "blocked", 1).then()
                    .onErrorResume(e -> Mono.empty());
            case "RATE_LIMITED" -> redisTemplate.opsForHash()
                    .increment(hourlyKey, "rate_limited", 1).then()
                    .onErrorResume(e -> Mono.empty());
            default -> Mono.empty();
        };
    }

    // ── getSummary ────────────────────────────────────────────────────────────

    @Override
    public Mono<AnalyticsSummary> getSummary(LocalDate date) {
        String dateStr     = date.format(DATE_FMT);
        String outcomeKey  = "analytics:outcomes:" + dateStr;
        String routeKey    = "analytics:route:req:" + dateStr;
        String tenantKey   = "analytics:tenant:req:" + dateStr;
        int    topN        = props.getTopN();

        Mono<OutcomeCounts> outcomeMono = readOutcomeCounts(outcomeKey);

        Mono<List<RouteStats>> topRoutesMono = redisTemplate.opsForZSet()
                .reverseRangeWithScores(routeKey, Range.closed(0L, (long) (topN - 1)))
                .map(t -> new RouteStats(t.getValue(), t.getScore().longValue()))
                .collectList()
                .onErrorReturn(List.of());

        Mono<List<TenantStats>> topTenantsMono = redisTemplate.opsForZSet()
                .reverseRangeWithScores(tenantKey, Range.closed(0L, (long) (topN - 1)))
                .map(t -> new TenantStats(t.getValue(), t.getScore().longValue()))
                .collectList()
                .onErrorReturn(List.of());

        return Mono.zip(outcomeMono, topRoutesMono, topTenantsMono)
                .map(tuple -> {
                    OutcomeCounts oc     = tuple.getT1();
                    List<RouteStats>  routes  = tuple.getT2();
                    List<TenantStats> tenants = tuple.getT3();

                    long total   = oc.allowed + oc.blockedWaf + oc.unauthenticated
                                 + oc.unauthorized + oc.rateLimited + oc.errors + oc.notFound;
                    double errorRate = total > 0 ? (double) (total - oc.allowed) / total * 100.0 : 0.0;

                    return new AnalyticsSummary(
                            dateStr, total, oc.allowed, oc.blockedWaf,
                            oc.unauthenticated, oc.unauthorized, oc.rateLimited,
                            oc.errors, errorRate, routes, tenants);
                })
                .onErrorReturn(new AnalyticsSummary(dateStr, 0, 0, 0, 0, 0, 0, 0, 0.0, List.of(), List.of()));
    }

    // ── getRouteStats ─────────────────────────────────────────────────────────

    @Override
    public Flux<RouteStats> getRouteStats(LocalDate date) {
        String key = "analytics:route:req:" + date.format(DATE_FMT);
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, Range.unbounded())
                .map(t -> new RouteStats(t.getValue(), t.getScore().longValue()))
                .onErrorResume(e -> Flux.empty());
    }

    // ── getTenantStats ────────────────────────────────────────────────────────

    @Override
    public Flux<TenantStats> getTenantStats(LocalDate date) {
        String key = "analytics:tenant:req:" + date.format(DATE_FMT);
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, Range.unbounded())
                .map(t -> new TenantStats(t.getValue(), t.getScore().longValue()))
                .onErrorResume(e -> Flux.empty());
    }

    // ── getTimeline ───────────────────────────────────────────────────────────

    @Override
    public Flux<HourlyBucket> getTimeline(int hours) {
        LocalDateTime start = LocalDateTime.now().minusHours(hours - 1L)
                .withMinute(0).withSecond(0).withNano(0);

        return Flux.range(0, hours)
                .flatMapSequential(i -> {
                    LocalDateTime hour   = start.plusHours(i);
                    String hourLabel     = hour.format(HOUR_FMT);
                    String key           = "analytics:hourly:" + hourLabel;

                    return redisTemplate.opsForHash().entries(key)
                            .collectMap(
                                    e -> e.getKey().toString(),
                                    e -> parseLong(e.getValue().toString()))
                            .map(m -> new HourlyBucket(
                                    hourLabel,
                                    m.getOrDefault("total",       0L),
                                    m.getOrDefault("errors",      0L),
                                    m.getOrDefault("blocked",     0L),
                                    m.getOrDefault("rate_limited", 0L)))
                            .onErrorReturn(new HourlyBucket(hourLabel, 0, 0, 0, 0));
                });
    }

    // ── getSecurityStats ──────────────────────────────────────────────────────

    @Override
    public Mono<SecurityStats> getSecurityStats(LocalDate date) {
        String dateStr    = date.format(DATE_FMT);
        String outcomeKey = "analytics:outcomes:" + dateStr;

        return readOutcomeCounts(outcomeKey)
                .map(oc -> new SecurityStats(
                        dateStr, oc.blockedWaf, oc.unauthenticated,
                        oc.unauthorized, oc.rateLimited, oc.notFound))
                .onErrorReturn(new SecurityStats(dateStr, 0, 0, 0, 0, 0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Mono<OutcomeCounts> readOutcomeCounts(String outcomeKey) {
        return redisTemplate.opsForHash().entries(outcomeKey)
                .collectMap(
                        e -> e.getKey().toString(),
                        e -> parseLong(e.getValue().toString()))
                .map(m -> new OutcomeCounts(
                        m.getOrDefault("ALLOWED",         0L),
                        m.getOrDefault("BLOCKED_WAF",     0L),
                        m.getOrDefault("UNAUTHENTICATED", 0L),
                        m.getOrDefault("UNAUTHORIZED",    0L),
                        m.getOrDefault("RATE_LIMITED",    0L),
                        m.getOrDefault("ERROR",           0L),
                        m.getOrDefault("NOT_FOUND",       0L)))
                .onErrorReturn(new OutcomeCounts(0, 0, 0, 0, 0, 0, 0));
    }

    private static long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Internal value object holding all outcome counters from the outcomes hash. */
    private record OutcomeCounts(
            long allowed,
            long blockedWaf,
            long unauthenticated,
            long unauthorized,
            long rateLimited,
            long errors,
            long notFound
    ) {}
}
