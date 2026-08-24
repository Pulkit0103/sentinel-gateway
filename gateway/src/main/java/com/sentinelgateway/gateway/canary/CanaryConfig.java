package com.sentinelgateway.gateway.canary;

/**
 * Per-route canary configuration.
 *
 * <p>{@code canaryUri} is the full URI of the canary backend (e.g. {@code http://canary-v2:8080}).
 * {@code weight} is the percentage of requests (0–100) to route to the canary backend.
 */
public class CanaryConfig {

    /** URI of the canary backend, e.g. {@code http://canary-v2:8080}. */
    private String canaryUri;

    /** Percentage of requests to route to canary (0 = never, 100 = always). */
    private int weight;

    public String getCanaryUri() {
        return canaryUri;
    }

    public void setCanaryUri(String canaryUri) {
        this.canaryUri = canaryUri;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
