package com.sentinelgateway.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "sentinel.gateway.hello-service-url=http://localhost:8081"
})
class SentinelGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts successfully
        // with all beans wired correctly.
    }
}
