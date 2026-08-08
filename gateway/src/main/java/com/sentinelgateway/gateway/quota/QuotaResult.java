package com.sentinelgateway.gateway.quota;

/**
 * Result of a quota check.
 *
 * @param allowed         true if the tenant is within quota
 * @param limit           configured max requests for the period
 * @param used            requests consumed in the current period
 * @param period          the quota period (DAILY or MONTHLY)
 * @param resetEpochSecs  epoch second when the current window resets
 */
public record QuotaResult(boolean allowed, long limit, long used,
                           QuotaPeriod period, long resetEpochSecs) {

    public static QuotaResult allowed(long limit, long used, QuotaPeriod period, long resetEpochSecs) {
        return new QuotaResult(true, limit, used, period, resetEpochSecs);
    }

    public static QuotaResult exceeded(long limit, long used, QuotaPeriod period, long resetEpochSecs) {
        return new QuotaResult(false, limit, used, period, resetEpochSecs);
    }
}
