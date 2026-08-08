package com.sentinelgateway.gateway.quota;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Component;

/**
 * Redis-backed tenant quota checker.
 *
 * Key format: quota:{tenantId}:{period}:{window}
 *   period = daily | monthly
 *   window = date (yyyy-MM-dd) or year-month (yyyy-MM)
 *
 * Uses INCR + EXPIRE for atomic increment with TTL expiry at window boundary.
 * Active only when sentinel.quota.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "sentinel.quota.enabled", havingValue = "true")
public class RedisQuotaChecker implements QuotaChecker {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final QuotaProperties properties;

    public RedisQuotaChecker(ReactiveStringRedisTemplate redisTemplate,
                              QuotaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Mono<QuotaResult> checkAndIncrement(String tenantId, QuotaPeriod period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String window;
        long limit;
        long ttlSeconds;
        long resetEpoch;

        if (period == QuotaPeriod.DAILY) {
            window = today.toString();
            limit = properties.dailyLimitFor(tenantId);
            LocalDate tomorrow = today.plusDays(1);
            resetEpoch = tomorrow.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            ttlSeconds = resetEpoch - System.currentTimeMillis() / 1000;
        } else {
            window = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
            limit = properties.monthlyLimitFor(tenantId);
            LocalDate firstOfNextMonth = today.with(TemporalAdjusters.firstDayOfNextMonth());
            resetEpoch = firstOfNextMonth.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            ttlSeconds = resetEpoch - System.currentTimeMillis() / 1000;
        }

        String key = "quota:" + tenantId + ":" + period.name().toLowerCase() + ":" + window;
        long finalLimit = limit;
        long finalResetEpoch = resetEpoch;
        long finalTtl = Math.max(ttlSeconds, 1);

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count ->
                        redisTemplate.expire(key, Duration.ofSeconds(finalTtl))
                                .thenReturn(count))
                .map(count -> {
                    if (count > finalLimit) {
                        return QuotaResult.exceeded(finalLimit, count, period, finalResetEpoch);
                    }
                    return QuotaResult.allowed(finalLimit, count, period, finalResetEpoch);
                });
    }
}
