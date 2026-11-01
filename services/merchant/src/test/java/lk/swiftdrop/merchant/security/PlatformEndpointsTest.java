package lk.swiftdrop.merchant.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PlatformEndpointsTest extends AbstractIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f-]{36}";

    @Test
    void healthIsOpenToMonitoring() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void prometheusMetricsAreOpenToScraping() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void otherActuatorEndpointsAreNotAvailable() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        mockMvc.perform(get("/api/v1/other")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "from-gateway-1"))
                .andExpect(header().string("X-Correlation-Id", "from-gateway-1"));
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "bad id with spaces"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void configuredOriginMayCallTheApiFromABrowser() throws Exception {
        mockMvc.perform(preflight("http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("Idempotency-Key")));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(preflight("https://evil.example"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder preflight(String origin) {
        return options("/api/v1/merchants/me")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key");
    }
}
