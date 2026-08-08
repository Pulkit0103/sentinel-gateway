package com.sentinelgateway.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RequestIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthEndpoint_returnsXRequestIdHeader() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(RequestIdFilter.REQUEST_ID_HEADER);
    }

    @Test
    void whenRequestIdHeaderProvided_itIsPreserved() {
        webTestClient.get()
                .uri("/actuator/health")
                .header(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-12345")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestIdFilter.REQUEST_ID_HEADER, "test-request-id-12345");
    }

    @Test
    void whenNoRequestIdProvided_aNewUuidIsGenerated() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(RequestIdFilter.REQUEST_ID_HEADER, requestId -> {
                    assertThat(requestId).isNotBlank();
                    assertThat(requestId).matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
                });
    }
}
