package com.sentinelgateway.gateway.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the webhook event emission subsystem.
 *
 * Bound from the {@code sentinel.webhook} prefix in application.yml.
 *
 * When {@code enabled} is {@code false} (the default) the no-op implementation
 * is used and no HTTP calls are made.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.webhook")
public class WebhookProperties {

    /**
     * Master switch for webhook delivery. Defaults to {@code false}.
     */
    private boolean enabled = false;

    /**
     * List of webhook endpoint targets.
     */
    private List<WebhookEndpoint> endpoints = new ArrayList<>();

    /**
     * HTTP request timeout in seconds for each webhook POST. Defaults to 5.
     */
    private int timeoutSeconds = 5;

    /**
     * Number of retry attempts on failure. Defaults to 2.
     */
    private int retryAttempts = 2;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<WebhookEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<WebhookEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    // ── Inner class ──────────────────────────────────────────────────────────

    /**
     * A single webhook endpoint target.
     */
    public static class WebhookEndpoint {

        /**
         * The URL to POST events to.
         */
        private String url;

        /**
         * HMAC-SHA256 signing secret. Used to compute the {@code X-Webhook-Signature} header.
         */
        private String secret = "";

        /**
         * Subset of event types this endpoint should receive.
         * Empty list means all event types are forwarded.
         */
        private List<String> events = new ArrayList<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getEvents() {
            return events;
        }

        public void setEvents(List<String> events) {
            this.events = events;
        }
    }
}
