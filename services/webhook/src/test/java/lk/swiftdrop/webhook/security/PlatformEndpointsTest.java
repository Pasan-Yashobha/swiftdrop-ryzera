package lk.swiftdrop.webhook.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.TestAuth;
import org.junit.jupiter.api.Test;

class PlatformEndpointsTest extends AbstractIntegrationTest {

    @Test
    void healthAndMetricsAreOpen() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void everythingElseIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/whatever"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/whatever").with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void correlationIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries").header("X-Correlation-Id", "gateway-123")
                        .with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "gateway-123"));
    }
}
