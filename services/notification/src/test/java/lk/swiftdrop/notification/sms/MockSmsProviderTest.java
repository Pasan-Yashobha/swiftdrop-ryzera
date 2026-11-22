package lk.swiftdrop.notification.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.notification.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.notification.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MockSmsProviderTest extends AbstractIntegrationTest {

    private static final String URL = "/mock-sms/messages";

    @Test
    void storesMessagesWithAMaskedNumber() throws Exception {
        mockMvc.perform(post(URL).header("X-Api-Key", TestAuth.SMS_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"+94771234567\",\"message\":\"SwiftDrop: hi\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        assertThat(smsMessages.findAll()).singleElement().satisfies(m -> {
            assertThat(m.getToMasked()).isEqualTo("07X XXX 4567");
            assertThat(m.getMessage()).isEqualTo("SwiftDrop: hi");
        });
    }

    @Test
    void needsTheApiKeyAndAValidBody() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"1\",\"message\":\"m\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header("X-Api-Key", "wrong").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"1\",\"message\":\"m\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header("X-Api-Key", TestAuth.SMS_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(URL).header("X-Api-Key", TestAuth.SMS_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void internalEndpointsNeedTheInternalToken() throws Exception {
        mockMvc.perform(post("/internal/anything")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/anything").header("X-Internal-Token", TestAuth.INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
    }
}
