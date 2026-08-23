package com.sentinelgateway.gateway.revocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Admin endpoint for revoking JWTs before their natural expiry.
 *
 * Requires {@code ADMIN} role — enforced via {@code @PreAuthorize}.
 *
 * {@code POST /admin/revoke} — revoke a token by JTI:
 *   - Body: {@code {"jti":"...","expiresAt":"2026-08-24T10:00:00Z"}}
 *   - 204 No Content on success
 *   - 400 Bad Request if the token is already expired (no need to revoke)
 */
@RestController
@RequestMapping("/admin/revoke")
public class RevocationController {

    private static final Logger log = LoggerFactory.getLogger(RevocationController.class);

    private final TokenRevocationService tokenRevocationService;

    public RevocationController(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> revokeToken(@RequestBody RevocationRequest request) {
        Instant now = Instant.now();
        long remainingTtl = ChronoUnit.SECONDS.between(now, request.expiresAt());

        if (remainingTtl <= 0) {
            log.debug("Revocation request for JTI={} rejected — token already expired", request.jti());
            return Mono.just(ResponseEntity.badRequest().<Void>build());
        }

        log.info("Revoking token JTI={} with remainingTtl={}s", request.jti(), remainingTtl);
        return tokenRevocationService.revokeToken(request.jti(), Duration.ofSeconds(remainingTtl))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
