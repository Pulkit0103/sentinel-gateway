package com.sentinelgateway.gateway.lifecycle;

/**
 * Abstraction over application shutdown, allowing tests to mock it without
 * accidentally closing the real Spring ApplicationContext.
 */
@FunctionalInterface
public interface ShutdownHandler {
    void shutdown();
}
