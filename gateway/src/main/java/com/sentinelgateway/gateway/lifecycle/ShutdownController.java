package com.sentinelgateway.gateway.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Admin endpoint for triggering a programmatic graceful shutdown.
 *
 * POST /admin/shutdown signals the gateway to drain in-flight requests
 * (up to DrainProperties.timeoutSeconds) then close the application context.
 *
 * The close() call is deferred 200ms so the HTTP response is flushed before
 * the Netty event loop tears down.
 *
 * Secured by the existing SecurityWebFilterChain: requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/shutdown")
public class ShutdownController {

    private static final Logger log = LoggerFactory.getLogger(ShutdownController.class);

    private final ShutdownHandler shutdownHandler;
    private final DrainProperties drainProperties;

    public ShutdownController(ShutdownHandler shutdownHandler, DrainProperties drainProperties) {
        this.shutdownHandler = shutdownHandler;
        this.drainProperties = drainProperties;
    }

    @PostMapping
    public Mono<Map<String, Object>> shutdown() {
        int timeoutSeconds = drainProperties.getTimeoutSeconds();
        log.warn("Graceful shutdown requested — draining for up to {}s", timeoutSeconds);

        // Defer shutdown so response is written before context closes
        Mono.delay(Duration.ofMillis(200))
                .subscribe(x -> {
                    log.info("Closing application context");
                    shutdownHandler.shutdown();
                });

        return Mono.just(Map.of(
                "status", "shutting_down",
                "drainTimeoutSeconds", timeoutSeconds
        ));
    }
}
