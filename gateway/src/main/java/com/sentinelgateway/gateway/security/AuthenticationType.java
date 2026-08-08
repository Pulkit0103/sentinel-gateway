package com.sentinelgateway.gateway.security;

/**
 * How the caller authenticated to the gateway.
 * The authentication type is propagated to upstream services as context.
 * Additional types (API_KEY, mTLS) are added in later phases.
 */
public enum AuthenticationType {
    JWT,
    API_KEY
}
