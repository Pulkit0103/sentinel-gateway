package com.sentinelgateway.hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHello_returns200WithExpectedJson() throws Exception {
        mockMvc.perform(get("/api/hello").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Hello from Sentinel Gateway!"))
                .andExpect(jsonPath("$.service").value("hello-service"))
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void getHello_withRequestIdHeader_echoesItInResponse() throws Exception {
        String requestId = "test-req-abc-123";

        mockMvc.perform(get("/api/hello")
                        .header("X-Request-ID", requestId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void getHello_withoutRequestIdHeader_hasNullRequestId() throws Exception {
        mockMvc.perform(get("/api/hello").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void actuatorHealth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
